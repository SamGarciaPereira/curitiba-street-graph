import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.xml.stream.*;

// converte a malha viária de Curitiba (OpenStreetMap) em dois CSVs:
//   nos.csv     -> id, osm_id, latitude, longitude
//   arestas.csv -> origem, destino, comprimento_m, tipo_via, nome_rua, mao_unica, velocidade_max, osm_way_id
// ideia central:
//   - o mapa traz "ways" (ruas) formados por uma sequência de pontos (nodes)
//   - vira nó do grafo o ponto que é cruzamento (aparece em >=2 ruas) ou ponta de rua (início/fim)
//   - vira aresta cada trecho entre dois nós consecutivos; o peso é o comprimento em metros
//   - os pontos que sobram são só curva da rua: entram apenas na soma do comprimento
// fonte dos dados: (c) colaboradores do OpenStreetMap, licença ODbL
public class Conversor {

    // guarda um "way" (rua) do OSM: a ordem dos pontos e as poucas tags que usamos
    static class Way {
        long id;
        List<Long> nds = new ArrayList<>(); // pontos na ordem em que a rua é desenhada
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

        Set<Long> ehNo = descobrirNos(ways, coord);        // quais pontos viram nó do grafo
        Map<Long, Integer> idDe = numerarNos(ways, ehNo);  // dá um id 1..N a cada nó

        escreverNos(saida, idDe, coord);
        int totalArestas = escreverArestas(saida, ways, ehNo, idDe, coord);

        System.out.println("nos.csv: " + idDe.size() + " nos");
        System.out.println("arestas.csv: " + totalArestas + " arestas");
    }

    // descobre quais pontos viram nó do grafo:
    // cruzamento (aparece em >=2 ruas distintas) ou ponta de rua (início/fim de uma via)
    static Set<Long> descobrirNos(List<Way> ways, Map<Long, double[]> coord) {
        Map<Long, Integer> emQuantasRuas = new HashMap<>();
        Set<Long> ponta = new HashSet<>();
        for (Way w : ways) {
            for (Long n : new HashSet<>(w.nds))        // set: não contar duas vezes na mesma rua
                emQuantasRuas.merge(n, 1, Integer::sum);
            if (!w.nds.isEmpty()) {
                ponta.add(w.nds.get(0));
                ponta.add(w.nds.get(w.nds.size() - 1));
            }
        }
        Set<Long> ehNo = new HashSet<>();
        for (Long n : coord.keySet())
            if (emQuantasRuas.getOrDefault(n, 0) >= 2 || ponta.contains(n))
                ehNo.add(n);
        return ehNo;
    }

    // dá um id sequencial 1..N aos nós, na ordem em que aparecem nas ruas (determinístico)
    static Map<Long, Integer> numerarNos(List<Way> ways, Set<Long> ehNo) {
        Map<Long, Integer> idDe = new LinkedHashMap<>();
        int proximoId = 1;
        for (Way w : ways)
            for (Long n : w.nds)
                if (ehNo.contains(n) && !idDe.containsKey(n))
                    idDe.put(n, proximoId++);
        return idDe;
    }

    // escreve nos.csv na mesma ordem dos ids: id, osm_id, latitude, longitude
    static void escreverNos(String saida, Map<Long, Integer> idDe, Map<Long, double[]> coord) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(Path.of(saida, "nos.csv"), StandardCharsets.UTF_8)) {
            for (Map.Entry<Long, Integer> e : idDe.entrySet()) {
                double[] c = coord.get(e.getKey());
                out.write(e.getValue() + "," + e.getKey() + ","
                        + String.format(Locale.US, "%.7f", c[0]) + ","
                        + String.format(Locale.US, "%.7f", c[1]));
                out.newLine();
            }
        }
    }

    // escreve arestas.csv quebrando cada rua nos nós do grafo; devolve quantas gravou
    static int escreverArestas(String saida, List<Way> ways, Set<Long> ehNo,
                               Map<Long, Integer> idDe, Map<Long, double[]> coord) throws Exception {
        int totalArestas = 0;
        try (BufferedWriter out = Files.newBufferedWriter(Path.of(saida, "arestas.csv"), StandardCharsets.UTF_8)) {
            for (Way w : ways) {
                boolean maoUnica = "yes".equals(w.oneway) || "true".equals(w.oneway) || "1".equals(w.oneway);
                boolean inverte  = "-1".equals(w.oneway) || "reverse".equals(w.oneway); // sentido contrário ao desenho
                String vel = velocidade(w.maxspeed);

                int i = 0;
                while (i < w.nds.size() - 1) {
                    long inicioTrecho = w.nds.get(i);
                    double metros = 0;
                    int j = i + 1;
                    // caminha somando a distância ponto a ponto até achar o próximo nó do grafo
                    for (; j < w.nds.size(); j++) {
                        metros += haversine(coord.get(w.nds.get(j - 1)), coord.get(w.nds.get(j)));
                        if (ehNo.contains(w.nds.get(j))) break;
                    }
                    long fimTrecho = w.nds.get(j);
                    int o = idDe.get(inicioTrecho), d = idDe.get(fimTrecho);
                    if (o != d) {                         // descarta laço (trecho que volta ao mesmo nó)
                        if (inverte) { int t = o; o = d; d = t; }
                        out.write(o + "," + d + ","
                                + String.format(Locale.US, "%.2f", metros) + ","
                                + csv(w.highway) + "," + csv(w.name) + ","
                                + (maoUnica || inverte) + "," + vel + "," + w.id);
                        out.newLine();
                        totalArestas++;
                    }
                    i = j; // próximo trecho começa onde este terminou
                }
            }
        }
        return totalArestas;
    }

    // lê o .osm em uma passada (StAX = leitura em fluxo, pouca memória)
    // os "ways" vêm antes dos "nodes" no arquivo, por isso guarda tudo e processa depois
    static void lerOsm(String caminho, Map<Long, double[]> coord, List<Way> ways) throws Exception {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false); // ignora DTD/entidades externas (segurança)
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
            case "oneway"   -> w.oneway   = v; // se é mão única
            case "maxspeed" -> w.maxspeed = v; // velocidade máxima
            default         -> { }
        }
    }

    // distância em metros entre duas coordenadas (fórmula de haversine, Terra como esfera)
    static double haversine(double[] a, double[] b) {
        if (a == null || b == null) return 0;
        double R = 6_371_000.0;
        double p1 = Math.toRadians(a[0]), p2 = Math.toRadians(b[0]);
        double dLat = Math.toRadians(b[0] - a[0]), dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    // mantém só o número do maxspeed; se vier vazio ou texto (ex.: "BR:urban"), deixa em branco
    static String velocidade(String s) {
        if (s == null) return "";
        try { return String.valueOf(Integer.parseInt(s.trim())); }
        catch (NumberFormatException e) { return ""; }
    }

    // campo CSV: entre aspas quando tiver vírgula/aspas/quebra de linha; null vira vazio
    static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
