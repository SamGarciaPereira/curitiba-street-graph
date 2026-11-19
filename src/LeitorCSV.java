import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// vértice = interseção ou ponto da malha viária de Curitiba (rótulo: id do OpenStreetMap)
// aresta = trecho de rua entre dois pontos (peso: comprimento em metros; rótulo: nome da rua)
// direcionado porque rua de mão única só anda num sentido; mão dupla vira dois arcos, um em cada sentido
public final class LeitorCSV {

    private LeitorCSV() {
    }

    public static Grafo carregar(String caminhoNos, String caminhoArestas) throws IOException {
        Grafo g = new Grafo(true);
        // no CSV os nós começam em 1 e no Grafo em 0; este dicionário traduz um no outro
        Map<String, Integer> indicePorId = new HashMap<>();

        // lê nos.csv linha por linha
        try (BufferedReader in = Files.newBufferedReader(Path.of(caminhoNos), StandardCharsets.UTF_8)) {
            String linha;
            int numLinha = 0;

            while ((linha = in.readLine()) != null) {
                numLinha++;
                if (linha.isBlank()) {
                    continue;
                }

                // -1 não deixa o split descartar colunas vazias no fim da linha
                String[] c = linha.split(",", -1);

                // descarta a 1ª linha só se for cabeçalho (o CSV pode vir com ou sem ele)
                if (numLinha == 1 && ehCabecalho(c)) {
                    continue;
                }

                if (c.length < 2) {
                    throw new IOException(caminhoNos + ", linha " + numLinha + ": colunas insuficientes");
                }
                String id = c[0].strip();
                String osmId = c[1].strip();
                if (indicePorId.containsKey(id)) {
                    throw new IOException(caminhoNos + ", linha " + numLinha + ": id repetido " + id);
                }

                // cria o vértice (rótulo = osm_id) e guarda no dicionário o índice dele
                indicePorId.put(id, g.adicionarVertice(osmId));
            }
        }

        // lê arestas.csv
        try (BufferedReader in = Files.newBufferedReader(Path.of(caminhoArestas), StandardCharsets.UTF_8)) {
            String linha;
            int numLinha = 0;
            while ((linha = in.readLine()) != null) {
                numLinha++;
                if (linha.isBlank()) {
                    continue;
                }
                String[] c = linha.split(",", -1);
                if (numLinha == 1 && ehCabecalho(c)) {
                    continue;
                }
                if (c.length < 6) {
                    throw new IOException(caminhoArestas + ", linha " + numLinha + ": colunas insuficientes");
                }
                Integer u = indicePorId.get(c[0].strip());
                Integer v = indicePorId.get(c[1].strip());
                // se a aresta aponta para um nó que não existe em nos.csv, o get devolve null
                if (u == null || v == null) {
                    throw new IOException(caminhoArestas + ", linha " + numLinha + ": nó inexistente");
                }
                double comprimento = Double.parseDouble(c[2].strip());
                String tipoVia = c[3].strip();
                String nomeRua = c[4].strip();
                boolean maoUnica = ehMaoUnica(c[5]);
                String rotulo = nomeRua.isEmpty() ? tipoVia : nomeRua;

                g.adicionarAresta(u, v, comprimento, rotulo);
                if (!maoUnica) {
                    g.adicionarAresta(v, u, comprimento, rotulo);
                }
            }
        }
        return g;
    }

    // é cabeçalho se a primeira coluna não for um número (ex.: "id" ou "origem")
    private static boolean ehCabecalho(String[] colunas) {
        String primeira = colunas[0].replace("﻿", "").strip();
        try {
            Long.parseLong(primeira);
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    // aceita os dois formatos gerados pelo Conversor: "sim"/"nao" e "true"/"false"
    private static boolean ehMaoUnica(String valor) {
        String v = valor.strip().toLowerCase();
        return v.equals("sim") || v.equals("true") || v.equals("yes") || v.equals("1");
    }
}
