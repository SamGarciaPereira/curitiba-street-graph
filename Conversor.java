import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.xml.stream.*;

/**
 * Conversor da malha viaria de Curitiba (OpenStreetMap) para dois CSVs:
 *   nos.csv     -> id, osm_id, latitude, longitude
 *   arestas.csv -> origem, destino, comprimento_m, tipo_via, nome_rua, mao_unica, velocidade_max, osm_way_id
 *
 * Ideia central:
 *   - O mapa traz "ways" (ruas) formados por uma sequencia de pontos (nodes).
 *   - Vira NO do grafo o ponto que e cruzamento (aparece em >=2 ruas) ou ponta de rua (inicio/fim).
 *   - Vira ARESTA cada trecho de rua entre dois nos consecutivos; o peso e o comprimento em metros.
 *   - Os pontos que sobram sao so curva da rua: entram apenas na soma do comprimento.
 *
 * Fonte dos dados: (c) colaboradores do OpenStreetMap, licenca ODbL.
 */
public class Conversor {

    // Guarda um "way" (rua) do OSM: a ordem dos pontos e as poucas tags que usamos.
    static class Way {
        long id;
        List<Long> nds = new ArrayList<>(); // pontos na ordem em que a rua e desenhada
        String highway, name, oneway, maxspeed;
        Way(long id) { this.id = id; }
    }

    public static void main(String[] args) throws Exception {
        String entrada = args.length > 0 ? args[0] : "curitiba.osm";
        String saida   = args.length > 1 ? args[1] : ".";

        // node id -> [latitude, longitude]
        Map<Long, double[]> coord = new HashMap<>();
        List<Way> ways = new ArrayList<>();

        lerOsm(entrada, coord, ways);

        // --- Descobrir quais pontos sao nos do grafo ---
        // Conta em quantas ruas DISTINTAS cada ponto aparece, e marca os que sao ponta de rua.
        Map<Long, Integer> emQuantasRuas = new HashMap<>();
        Set<Long> ponta = new HashSet<>();
        for (Way w : ways) {
            for (Long n : new HashSet<>(w.nds))        // set: nao contar duas vezes na mesma rua
                emQuantasRuas.merge(n, 1, Integer::sum);
            if (!w.nds.isEmpty()) {
                ponta.add(w.nds.get(0));
                ponta.add(w.nds.get(w.nds.size() - 1));
            }
        }
        // No = cruzamento (>=2 ruas) OU ponta de rua.
        Set<Long> ehNo = new HashSet<>();
        for (Long n : coord.keySet())
            if (emQuantasRuas.getOrDefault(n, 0) >= 2 || ponta.contains(n))
                ehNo.add(n);

        // --- Dar um id sequencial 1..N aos nos, na ordem em que aparecem (deterministico) ---
        Map<Long, Integer> idDe = new LinkedHashMap<>();
        int proximoId = 1;
        for (Way w : ways)
            for (Long n : w.nds)
                if (ehNo.contains(n) && !idDe.containsKey(n))
                    idDe.put(n, proximoId++);

        // --- Escrever nos.csv (na mesma ordem dos ids) ---
        try (BufferedWriter out = Files.newBufferedWriter(Path.of(saida, "nos.csv"), StandardCharsets.UTF_8)) {
            for (Map.Entry<Long, Integer> e : idDe.entrySet()) {
                double[] c = coord.get(e.getKey());
                out.write(e.getValue() + "," + e.getKey() + ","
                        + String.format(Locale.US, "%.7f", c[0]) + ","
                        + String.format(Locale.US, "%.7f", c[1]));
                out.newLine();
            }
        }

        // --- Escrever arestas.csv quebrando cada rua nos nos do grafo ---
        int totalArestas = 0;
        try (BufferedWriter out = Files.newBufferedWriter(Path.of(saida, "arestas.csv"), StandardCharsets.UTF_8)) {
            for (Way w : ways) {
                boolean maoUnica = "yes".equals(w.oneway) || "true".equals(w.oneway) || "1".equals(w.oneway);
                boolean inverte  = "-1".equals(w.oneway) || "reverse".equals(w.oneway); // sentido contrario ao desenho
                String vel = velocidade(w.maxspeed);

                int i = 0;
                while (i < w.nds.size() - 1) {
                    long inicioTrecho = w.nds.get(i);
                    double metros = 0;
                    int j = i + 1;
                    // Caminha somando a distancia ponto a ponto ate achar o proximo no do grafo.
                    for (; j < w.nds.size(); j++) {
                        metros += haversine(coord.get(w.nds.get(j - 1)), coord.get(w.nds.get(j)));
                        if (ehNo.contains(w.nds.get(j))) break;
                    }
                    long fimTrecho = w.nds.get(j);
                    int o = idDe.get(inicioTrecho), d = idDe.get(fimTrecho);
                    if (o != d) {                         // descarta laco (trecho que volta ao mesmo no)
                        if (inverte) { int t = o; o = d; d = t; }
                        out.write(o + "," + d + ","
                                + String.format(Locale.US, "%.2f", metros) + ","
                                + csv(w.highway) + "," + csv(w.name) + ","
                                + (maoUnica || inverte) + "," + vel + "," + w.id);
                        out.newLine();
                        totalArestas++;
                    }
                    i = j; // proximo trecho comeca onde este terminou
                }
            }
        }

        System.out.println("nos.csv: " + idDe.size() + " nos");
        System.out.println("arestas.csv: " + totalArestas + " arestas");
    }

    // Le o .osm em uma passada (StAX = leitura em fluxo, pouca memoria).
    // Os "ways" vem antes dos "nodes" no arquivo, por isso guardamos tudo e processamos depois.
    static void lerOsm(String caminho, Map<Long, double[]> coord, List<Way> ways) throws Exception {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false); // ignora DTD/entidades externas (seguranca)
        XMLStreamReader r = f.createXMLStreamReader(new FileInputStream(caminho), "UTF-8");
        Way atual = null;
        while (r.hasNext()) {
            if (r.next() != XMLStreamConstants.START_ELEMENT) {
                if (r.getEventType() == XMLStreamConstants.END_ELEMENT && "way".equals(r.getLocalName())) {
                    ways.add(atual);
                    atual = null;
                }
                continue;
            }
            switch (r.getLocalName()) {
                case "node" -> coord.put(
                        Long.parseLong(r.getAttributeValue(null, "id")),
                        new double[]{ Double.parseDouble(r.getAttributeValue(null, "lat")),
                                      Double.parseDouble(r.getAttributeValue(null, "lon")) });
                case "way"  -> atual = new Way(Long.parseLong(r.getAttributeValue(null, "id")));
                case "nd"   -> { if (atual != null) atual.nds.add(Long.parseLong(r.getAttributeValue(null, "ref"))); }
                case "tag"  -> { if (atual != null) guardarTag(atual, r.getAttributeValue(null, "k"), r.getAttributeValue(null, "v")); }
                default     -> { }
            }
        }
        r.close();
    }

    static void guardarTag(Way w, String k, String v) {
        switch (k) {
            case "highway"  -> w.highway  = v; // tipo da via (residential, primary, ...)
            case "name"     -> w.name     = v; // nome da rua
            case "oneway"   -> w.oneway   = v; // se e mao unica
            case "maxspeed" -> w.maxspeed = v; // velocidade maxima
            default         -> { }
        }
    }

    // Distancia em metros entre duas coordenadas (formula de haversine, Terra como esfera).
    static double haversine(double[] a, double[] b) {
        if (a == null || b == null) return 0;
        double R = 6_371_000.0;
        double p1 = Math.toRadians(a[0]), p2 = Math.toRadians(b[0]);
        double dLat = Math.toRadians(b[0] - a[0]), dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    // Mantem so o numero do maxspeed; se vier vazio ou texto (ex.: "BR:urban"), deixa em branco.
    static String velocidade(String s) {
        if (s == null) return "";
        try { return String.valueOf(Integer.parseInt(s.trim())); }
        catch (NumberFormatException e) { return ""; }
    }

    // Campo CSV: entre aspas quando tiver virgula/aspas/quebra de linha; null vira vazio.
    static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
