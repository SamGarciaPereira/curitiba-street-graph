import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

 // *Vertices 3
 // 1 "rótulo do vértice 1"
 // 2 "rótulo do vértice 2"
 // 3 "rótulo do vértice 3"
 // *Edges(não direcionado; *Arcs para direcionado)
 // Origem | Destino | Peso
 // 1 2 10
 // 1 3 4

// O Pajek faz duas coisas
// salvar(grafo, caminho) : pega o Grafo que está na memória e escreve um arquivo .net
// carregar(caminho) : lê um arquivo .net e devolve um Grafo
// No Pajek os vértices começam em 1. No Grafo começam em 0, por isso o código soma 1 ao gravar e subtrai 1 ao ler

public final class Pajek {

    private Pajek() {
    }

    // Gravar
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
                // Percorre cada vértice u, percorre cada aresta q sai de u
                // Grava u+1 v+1 peso
                for (Aresta a : grafo.getAdjacentes(u)) {
                    int v = a.getDestino();
                    // Isso evita duplicadas nos gragos não direcionados.
                    if (!grafo.isDirecionado() && v < u) {
                        continue;
                    }
                    out.write((u + 1) + " " + (v + 1) + " " + formatarPeso(a.getPeso()));
                    out.newLine();
                }
            }
        }
    }

    // Só coloca o rotulo entre ' '
    private static String aspas(String s) {
        return "\"" + (s == null ? "" : s.replace('"', '\'')) + "\"";
    }

    // Só pra deixar a formatação do peso igual a do professor.
    private static String formatarPeso(double p) {
        if (p == Math.rint(p) && Math.abs(p) < 1e15) {
            return String.valueOf((long) p);
        }
        return String.format(Locale.US, "%s", p);
    }

    // Carregamento
    // A leitura  é feita em 2 etapas, pois precisa saber se é direcionado ou não.
    // Etapa 1 : lê o arquivo inteiro e guarda tudo em listas temporárias
    // Etapa 2 : com o tipo já conhecido, cria o Grafo e coloca tudo dentro.

    // record é uma classe do Java só para guardar dados, cada aresta lida do arquivo vira uma ArestaLida.
    // o campo arco que diz se ele é direcional ou não direcional.
    private record ArestaLida(int origem, int destino, double peso, String rotulo, boolean arco) {
    }

    // Etapa 1
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
                linha = linha.strip(); // Tira os espaços das pontas

                // Tira caracteres invisíveis que alguns programas podem colocar na frente
                if (numLinha == 1 && !linha.isEmpty() && linha.charAt(0) == '﻿') {
                    linha = linha.substring(1);
                }
                // % é comentario no Pajek
                if (linha.isEmpty() || linha.startsWith("%")) {
                    continue;
                }

                // Sabe que é cabeçalho pelo *
                if (linha.startsWith("*")) {
                    String cab = linha.toLowerCase(Locale.ROOT);
                    if (cab.startsWith("*vertices")) {
                        String[] partes = linha.split("\\s+");
                        if (partes.length < 2) {
                            throw erro(numLinha, "*Vertices sem quantidade");
                        }
                        int n = Integer.parseInt(partes[1]); // ("*Vertices", "8") agr sabe quantos vértices tem
                        rotulosVertices = new String[n];
                        for (int i = 0; i < n; i++) {
                            rotulosVertices[i] = String.valueOf(i + 1);
                            // Preenche cada posição com um rotulo provisorio (n do vertice)
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

                // Separa uma linha em partes
                // Espaço -> Pula
                // Aspas " -> Procura a próxima aspa e paga tudo que está no meio como uma parte só , sem as aspas
                // Qualquer outro carectere -> Vai lendo até o próximo espaço
                List<String> t = tokenizar(linha);
                switch (secao) {
                    // Lê a linha de vértice
                    case 1 -> {
                        int id = Integer.parseInt(t.get(0));
                        if (id < 1 || id > rotulosVertices.length) {
                            throw erro(numLinha, "vértice " + id + " fora do intervalo 1.." + rotulosVertices.length);
                        }
                        if (t.size() > 1) {
                            rotulosVertices[id - 1] = t.get(1);
                        }
                    }
                    // Lê a linha de aresta
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
                        // Esse i aponta pra 3 parte da linha
                        int i = 2;
                        // Existe uma 3 linha ? É um número ?
                        if (i < t.size() && ehNumero(t.get(i))) {
                            peso = Double.parseDouble(t.get(i));
                            i++;
                        }
                        // rótulo: "l \"texto\"" (padrão Pajek)
                        // Ele usa o i de cima e procura o rotulo na linha
                        for (; i < t.size(); i++) {
                            if (t.get(i).equals("l") && i + 1 < t.size()) {
                                rotulo = t.get(i + 1);
                                break;
                            }
                        }
                        // Guarda na lista temporária
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

        // Etapa 2
        Grafo g = new Grafo(temArcos);
        // Adiciona todos os rótulos lidos e guardados dos vértices
        for (String r : rotulosVertices) {
            g.adicionarVertice(r);
        }
        for (ArestaLida a : arestas) {
            // Adiciona todas as arestas na lista temporária
            g.adicionarAresta(a.origem(), a.destino(), a.peso(), a.rotulo());
            // Esse if é caso tenha Arcs e Edges ao mesmo tempo, nesse caso o grafo vira direcionado
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
