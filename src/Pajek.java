import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// exemplo do formato Pajek:
// *Vertices 3
// 1 "rótulo do vértice 1"
// 2 "rótulo do vértice 2"
// 3 "rótulo do vértice 3"
// *Edges (não direcionado; *Arcs para direcionado)
// origem destino peso
// 1 2 10
// 1 3 4
//
// salvar(grafo, caminho): pega o grafo da memória e escreve um .net
// carregar(caminho): lê um .net e devolve um grafo
// no Pajek os vértices começam em 1 e no Grafo em 0, por isso soma 1 ao gravar e subtrai 1 ao ler
public final class Pajek {

    private Pajek() {
    }

    // grava o grafo num .net
    public static void salvar(Grafo grafo, String caminho) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(Path.of(caminho), StandardCharsets.UTF_8)) {
            int n = grafo.getNumVertices();
            out.write("*Vertices " + n);
            out.newLine();
            for (int v = 0; v < n; v++) {
                out.write((v + 1) + " " + aspas(grafo.getRotulo(v)));
                out.newLine();
            }

            out.write(grafo.isDirecionado() ? "*Arcs" : "*Edges");
            out.newLine();
            for (int u = 0; u < n; u++) {
                // para cada vértice u, grava cada aresta que sai dele como "u+1 v+1 peso"
                for (Aresta a : grafo.getAdjacentes(u)) {
                    int v = a.getDestino();
                    // evita gravar a aresta duas vezes no grafo não direcionado
                    if (!grafo.isDirecionado() && v < u) {
                        continue;
                    }
                    out.write((u + 1) + " " + (v + 1) + " " + formatarPeso(a.getPeso()));
                    out.newLine();
                }
            }
        }
    }

    // põe o rótulo entre aspas duplas (troca aspa interna por ')
    private static String aspas(String s) {
        return "\"" + (s == null ? "" : s.replace('"', '\'')) + "\"";
    }

    // formata o peso: inteiro sem casas, senão como veio (igual ao exemplo do professor)
    private static String formatarPeso(double p) {
        if (p == Math.rint(p) && Math.abs(p) < 1e15) {
            return String.valueOf((long) p);
        }
        return String.format(Locale.US, "%s", p);
    }

    // carrega um .net em 2 etapas (precisa saber se é *Arcs ou *Edges antes de criar o grafo):
    // etapa 1: lê o arquivo inteiro guardando tudo em listas temporárias
    // etapa 2: com o tipo conhecido, cria o grafo e insere vértices e arestas

    // cada aresta lida vira uma ArestaLida; o campo arco diz se veio de *Arcs (direcionado)
    private record ArestaLida(int origem, int destino, double peso, String rotulo, boolean arco) {
    }

    // etapa 1
    public static Grafo carregar(String caminho) throws IOException {
        String[] rotulosVertices = null;
        List<ArestaLida> arestas = new ArrayList<>();
        boolean temArcos = false;

        // seção atual: 0 = nenhuma, 1 = vertices, 2 = arcs, 3 = edges, 4 = ignorada
        int secao = 0;
        int numLinha = 0;

        try (BufferedReader in = Files.newBufferedReader(Path.of(caminho), StandardCharsets.UTF_8)) {
            String linha;
            while ((linha = in.readLine()) != null) {
                numLinha++;
                linha = linha.strip(); // tira os espaços das pontas

                // tira o BOM que alguns programas colocam no início do arquivo
                if (numLinha == 1 && !linha.isEmpty() && linha.charAt(0) == '﻿') {
                    linha = linha.substring(1);
                }
                // % é comentário no Pajek
                if (linha.isEmpty() || linha.startsWith("%")) {
                    continue;
                }

                // linha de cabeçalho começa com *
                if (linha.startsWith("*")) {
                    String cab = linha.toLowerCase(Locale.ROOT);
                    if (cab.startsWith("*vertices")) {
                        String[] partes = linha.split("\\s+");
                        if (partes.length < 2) {
                            throw erro(numLinha, "*Vertices sem quantidade");
                        }
                        int n = Integer.parseInt(partes[1]); // ex.: "*Vertices 8" -> agora sabe quantos vértices tem
                        rotulosVertices = new String[n];
                        for (int i = 0; i < n; i++) {
                            rotulosVertices[i] = String.valueOf(i + 1);
                            // rótulo provisório = número do vértice (trocado ao ler a seção de vértices)
                        }
                        secao = 1;
                    } else if (cab.startsWith("*arcs") && !cab.startsWith("*arcslist")) {
                        secao = 2;
                        temArcos = true;
                    } else if (cab.startsWith("*edges") && !cab.startsWith("*edgeslist")) {
                        secao = 3;
                    } else {
                        secao = 4; // seção não suportada (ex.: *Arcslist, *Matrix, *Network)
                        if (cab.startsWith("*network")) {
                            secao = 0;
                        }
                    }
                    continue;
                }

                // quebra a linha em tokens (ver tokenizar): texto entre aspas vira um token só
                List<String> t = tokenizar(linha);
                switch (secao) {
                    // linha de vértice
                    case 1 -> {
                        int id = Integer.parseInt(t.get(0));
                        if (id < 1 || id > rotulosVertices.length) {
                            throw erro(numLinha, "vértice " + id + " fora do intervalo 1.." + rotulosVertices.length);
                        }
                        if (t.size() > 1) {
                            rotulosVertices[id - 1] = t.get(1);
                        }
                    }
                    // linha de aresta
                    case 2, 3 -> {
                        if (rotulosVertices == null) {
                            throw erro(numLinha, "aresta antes de *Vertices");
                        }
                        if (t.size() < 2) {
                            throw erro(numLinha, "aresta incompleta");
                        }
                        int u = Integer.parseInt(t.get(0)) - 1;
                        int v = Integer.parseInt(t.get(1)) - 1;
                        double peso = 1.0;
                        String rotulo = "";
                        // i aponta para o 3º token (depois de origem e destino)
                        int i = 2;
                        // se o 3º token for número, é o peso
                        if (i < t.size() && ehNumero(t.get(i))) {
                            peso = Double.parseDouble(t.get(i));
                            i++;
                        }
                        // rótulo opcional no padrão Pajek: l "texto"
                        for (; i < t.size(); i++) {
                            if (t.get(i).equals("l") && i + 1 < t.size()) {
                                rotulo = t.get(i + 1);
                                break;
                            }
                        }
                        // guarda na lista temporária (arco = veio de *Arcs)
                        arestas.add(new ArestaLida(u, v, peso, rotulo, secao == 2));
                    }
                    default -> {
                        // linhas fora de seções conhecidas são ignoradas
                    }
                }
            }
        }

        if (rotulosVertices == null) {
            throw new IOException("Arquivo Pajek sem seção *Vertices: " + caminho);
        }

        // etapa 2
        Grafo g = new Grafo(temArcos);
        // adiciona os vértices com os rótulos lidos
        for (String r : rotulosVertices) {
            g.adicionarVertice(r);
        }
        for (ArestaLida a : arestas) {
            g.adicionarAresta(a.origem(), a.destino(), a.peso(), a.rotulo());
            // se o grafo é direcionado mas a aresta veio de *Edges, adiciona também a volta
            if (temArcos && !a.arco() && a.origem() != a.destino()) {
                g.adicionarAresta(a.destino(), a.origem(), a.peso(), a.rotulo());
            }
        }
        return g;
    }

    private static List<String> tokenizar(String linha) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int n = linha.length();
        while (i < n) {
            char c = linha.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                int fim = linha.indexOf('"', i + 1);
                if (fim < 0) {
                    fim = n;
                }
                tokens.add(linha.substring(i + 1, fim));
                i = fim + 1;
            } else {
                int ini = i;
                while (i < n && !Character.isWhitespace(linha.charAt(i))) {
                    i++;
                }
                tokens.add(linha.substring(ini, i));
            }
        }
        return tokens;
    }

    private static boolean ehNumero(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static IOException erro(int linha, String msg) {
        return new IOException("Erro no arquivo Pajek, linha " + linha + ": " + msg);
    }
}
