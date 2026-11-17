import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Funções de centralidade do grafo (distância do melhor caminho, calculada com Dijkstra):
 * - Centralidade de proximidade (closeness) de cada nó
 * - Centralidade de intermediação (betweenness) de cada nó
 * - Estimativa de tempo da intermediação (mede uma amostra de origens e extrapola)
 */

// O peso da aresta é a distância (no grafo de Curitiba, o comprimento do trecho em metros).
// Como o Dijkstra exige pesos não negativos, um peso negativo gera erro.
//
// Otimizações para o grafo de Curitiba (36.107 vértices e 51.699 trechos):
// 1) Antes das buscas, a lista de adjacência é copiada para vetores (int[] / double[]) no formato
//    "lista compacta": as arestas que saem de u ficam nas posições inicio[u] até inicio[u+1]-1.
//    Assim o Dijkstra não cria objetos nem usa List<Integer> (sem boxing), o que deixa a busca bem mais rápida.
// 2) A fila de prioridade do Dijkstra é um heap binário de vértices com "diminuir chave"
//    (cada vértice entra no heap uma única vez), feito em vetor.
// 3) Cada origem é independente das outras, então as n buscas são divididas entre as threads do processador.
//    Cada thread tem seus próprios vetores e, no fim, as somas parciais são juntadas.
//
// Complexidade (n = vértices, m = arestas):
// - Um Dijkstra com heap binário: O((n + m) log n).
// - Proximidade: um Dijkstra por vértice -> O(n (n + m) log n) de tempo e O(n + m) de memória.
// - Intermediação (algoritmo de Brandes): um Dijkstra por vértice + uma passada de acumulação O(n + m)
//   -> O(n (n + m) log n) de tempo e O(n + m) de memória por thread.
// - Com t threads, o tempo de parede cai para aproximadamente O(n (n + m) log n / t).

public final class Centralidade {

    private Centralidade() {
    }

    // ------------------------------------------------------------------ 10. Proximidade

    // Centralidade de proximidade de cada vértice v, usando a distância do menor caminho que SAI de v.
    // Fórmula clássica (grafo conexo): C(v) = (n - 1) / soma das distâncias de v até os outros vértices.
    // Como o grafo de Curitiba não é conexo (28 componentes), a soma seria infinita. Por isso usamos a
    // correção de Wasserman e Faust, que considera só os r vértices alcançáveis a partir de v (contando v):
    //     C(v) = ((r - 1) / soma) * ((r - 1) / (n - 1))
    // Se o grafo for conexo, r = n e a fórmula vira exatamente a clássica.
    // Vértice que não alcança ninguém fica com 0.
    public static double[] proximidade(Grafo g) {
        Rede rede = new Rede(g);
        int n = rede.n;
        double[] resultado = new double[n];
        if (n <= 1) {
            return resultado;
        }

        AtomicInteger proximaOrigem = new AtomicInteger(0);
        executarEmParalelo(() -> {
            Dijkstra d = new Dijkstra(rede);
            int s;
            while ((s = proximaOrigem.getAndIncrement()) < n) {
                int alcancados = d.executar(s);
                double soma = 0;
                // ordem[0] é a própria origem (distância 0), então começa em 1
                for (int i = 1; i < alcancados; i++) {
                    soma += d.dist[d.ordem[i]];
                }
                if (alcancados > 1 && soma > 0) {
                    double r1 = alcancados - 1;
                    resultado[s] = (r1 / soma) * (r1 / (n - 1));
                }
            }
        });
        return resultado;
    }

    // ------------------------------------------------------------------ 11. Intermediação

    // Centralidade de intermediação de cada vértice v (algoritmo de Brandes, com Dijkstra):
    //     B(v) = soma, para todo par s != v != t, de sigma_st(v) / sigma_st
    // sigma_st = número de menores caminhos de s até t; sigma_st(v) = quantos deles passam por v.
    // Para cada origem s:
    //   1) Dijkstra a partir de s, contando sigma[w] (quantos menores caminhos chegam em w) e guardando
    //      a ordem em que os vértices foram finalizados (do mais perto para o mais longe).
    //   2) Percorre essa ordem de trás para frente acumulando a "dependência" delta[v]:
    //      delta[v] = soma, para cada w com dist[w] == dist[v] + peso(v, w), de sigma[v] / sigma[w] * (1 + delta[w])
    //   3) Soma delta[v] em B(v) (exceto a própria origem).
    // Grafo não direcionado: cada par {s, t} é contado duas vezes (s->t e t->s), por isso divide por 2.
    // Devolve o valor bruto (sem normalizar). Para o valor entre 0 e 1, use normalizarIntermediacao.
    public static double[] intermediacao(Grafo g) {
        Rede rede = new Rede(g);
        int[] origens = new int[rede.n];
        for (int i = 0; i < rede.n; i++) {
            origens[i] = i;
        }
        return intermediacao(g, rede, origens);
    }

    // Faz o Brandes só para as origens informadas (usado também pela estimativa de tempo).
    private static double[] intermediacao(Grafo g, Rede rede, int[] origens) {
        int n = rede.n;
        List<double[]> parciais = new ArrayList<>();
        AtomicInteger proxima = new AtomicInteger(0);

        executarEmParalelo(() -> {
            Dijkstra d = new Dijkstra(rede);
            double[] delta = new double[n];
            double[] parcial = new double[n];
            int i;
            while ((i = proxima.getAndIncrement()) < origens.length) {
                int s = origens[i];
                int alcancados = d.executar(s);

                // Percorre do vértice mais distante para o mais próximo da origem
                for (int k = alcancados - 1; k >= 0; k--) {
                    int v = d.ordem[k];
                    double dep = 0;
                    for (int e = rede.inicio[v]; e < rede.inicio[v + 1]; e++) {
                        int w = rede.destino[e];
                        // w vem depois de v em algum menor caminho a partir de s
                        // (a 2ª condição garante que w foi finalizado depois de v, igual ao que o Dijkstra contou em sigma)
                        if (d.dist[v] + rede.peso[e] == d.dist[w] && d.indiceOrdem[w] > k) {
                            dep += d.sigma[v] / d.sigma[w] * (1 + delta[w]);
                        }
                    }
                    delta[v] = dep;
                    if (v != s) {
                        parcial[v] += dep;
                    }
                }
                // Zera só os vértices usados nesta busca (mais rápido que zerar o vetor todo)
                for (int k = 0; k < alcancados; k++) {
                    delta[d.ordem[k]] = 0;
                }
            }
            synchronized (parciais) {
                parciais.add(parcial);
            }
        });

        // Junta as somas de cada thread
        double[] resultado = new double[n];
        for (double[] parcial : parciais) {
            for (int v = 0; v < n; v++) {
                resultado[v] += parcial[v];
            }
        }
        if (!g.isDirecionado()) {
            for (int v = 0; v < n; v++) {
                resultado[v] /= 2;
            }
        }
        return resultado;
    }

    // Normaliza a intermediação para ficar entre 0 e 1, dividindo pelo número de pares (s, t) sem v:
    // - direcionado: (n - 1)(n - 2)
    // - não direcionado: (n - 1)(n - 2) / 2
    public static double[] normalizarIntermediacao(Grafo g, double[] bruto) {
        int n = g.getNumVertices();
        double[] normalizado = new double[bruto.length];
        if (n <= 2) {
            return normalizado;
        }
        double pares = (double) (n - 1) * (n - 2);
        if (!g.isDirecionado()) {
            pares /= 2;
        }
        for (int v = 0; v < bruto.length; v++) {
            normalizado[v] = bruto[v] / pares;
        }
        return normalizado;
    }

    // ------------------------------------------------------------------ Medição de tempo

    // Estima o tempo (em ms) da intermediação no grafo inteiro: roda o Brandes para uma amostra de
    // origens sorteadas e extrapola. Como cada origem custa O((n + m) log n), o tempo total é
    // proporcional ao número de origens: tempoTotal ≈ tempoAmostra * n / amostra.
    public static long estimarTempoIntermediacao(Grafo g, int amostra) {
        Rede rede = new Rede(g);
        int n = rede.n;
        if (n == 0) {
            return 0;
        }
        amostra = Math.max(1, Math.min(amostra, n));

        // Sorteia "amostra" origens diferentes (embaralhamento parcial com semente fixa)
        int[] todos = new int[n];
        for (int i = 0; i < n; i++) {
            todos[i] = i;
        }
        Random rnd = new Random(42);
        for (int i = 0; i < amostra; i++) {
            int j = i + rnd.nextInt(n - i);
            int t = todos[i];
            todos[i] = todos[j];
            todos[j] = t;
        }
        int[] origens = Arrays.copyOf(todos, amostra);

        long ini = System.nanoTime();
        intermediacao(g, rede, origens);
        long tempoAmostra = System.nanoTime() - ini;
        return Math.round(tempoAmostra / 1e6 * n / amostra);
    }

    // ------------------------------------------------------------------ Auxiliares de exibição

    // Devolve os índices dos k vértices com maior valor (do maior para o menor).
    public static int[] maiores(double[] valores, int k) {
        k = Math.min(k, valores.length);
        Integer[] indices = new Integer[valores.length];
        for (int i = 0; i < valores.length; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Double.compare(valores[b], valores[a]));
        int[] top = new int[k];
        for (int i = 0; i < k; i++) {
            top[i] = indices[i];
        }
        return top;
    }

    // Grava o valor de cada vértice em CSV: vertice (numeração do Pajek, começando em 1), rotulo, valor
    public static void salvarCSV(Grafo g, double[] valores, String nomeColuna, String caminho) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(Path.of(caminho), StandardCharsets.UTF_8)) {
            out.write("vertice,rotulo," + nomeColuna);
            out.newLine();
            for (int v = 0; v < valores.length; v++) {
                out.write((v + 1) + "," + g.getRotulo(v).replace(",", " ") + ","
                        + String.format(Locale.US, "%.10g", valores[v]));
                out.newLine();
            }
        }
    }

    // ------------------------------------------------------------------ Estruturas internas

    // Executa a mesma tarefa em uma thread por núcleo do processador e espera todas terminarem.
    private static void executarEmParalelo(Runnable tarefa) {
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        Thread[] threads = new Thread[numThreads];
        RuntimeException[] erro = new RuntimeException[1];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                try {
                    tarefa.run();
                } catch (RuntimeException e) {
                    synchronized (erro) {
                        erro[0] = e;
                    }
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Cálculo interrompido.");
            }
        }
        if (erro[0] != null) {
            throw erro[0];
        }
    }

    // Cópia do grafo em vetores ("lista compacta"): as arestas que saem de u estão em
    // destino[inicio[u] .. inicio[u+1]-1] e peso[inicio[u] .. inicio[u+1]-1].
    // Laços (u -> u) são ignorados, pois nunca fazem parte de um menor caminho.
    private static final class Rede {
        final int n;
        final int[] inicio;
        final int[] destino;
        final double[] peso;

        Rede(Grafo g) {
            n = g.getNumVertices();
            inicio = new int[n + 1];
            int total = 0;
            for (int u = 0; u < n; u++) {
                inicio[u] = total;
                for (Aresta a : g.getAdjacentes(u)) {
                    if (a.getDestino() != u) {
                        total++;
                    }
                }
            }
            inicio[n] = total;
            destino = new int[total];
            peso = new double[total];
            int e = 0;
            for (int u = 0; u < n; u++) {
                for (Aresta a : g.getAdjacentes(u)) {
                    if (a.getDestino() == u) {
                        continue;
                    }
                    if (a.getPeso() < 0) {
                        throw new IllegalArgumentException("Dijkstra não aceita peso negativo (aresta "
                                + (u + 1) + " -> " + (a.getDestino() + 1) + ").");
                    }
                    destino[e] = a.getDestino();
                    peso[e] = a.getPeso();
                    e++;
                }
            }
        }
    }

    // Dijkstra com heap binário de vértices. Cada thread cria o seu e reaproveita os vetores a cada origem.
    // Depois de executar(s):
    // - dist[v]  = menor distância de s até v (infinito se não alcança)
    // - sigma[v] = número de menores caminhos de s até v
    // - ordem[0..alcancados-1] = vértices na ordem em que foram finalizados (distância crescente)
    // - indiceOrdem[v] = posição de v no vetor ordem
    private static final class Dijkstra {
        private final Rede rede;
        final double[] dist;
        final double[] sigma;
        final int[] ordem;
        final int[] indiceOrdem;
        private final int[] heap;
        // posicao[v] = onde v está no heap (-1 = nunca entrou, -2 = já finalizado)
        private final int[] posicao;
        private int tamanhoHeap;
        // quantos vértices a busca anterior alcançou (para limpar só eles na próxima)
        private int alcancadosAnterior = 0;

        Dijkstra(Rede rede) {
            this.rede = rede;
            int n = rede.n;
            dist = new double[n];
            sigma = new double[n];
            ordem = new int[n];
            indiceOrdem = new int[n];
            heap = new int[n];
            posicao = new int[n];
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(posicao, -1);
        }

        // Roda o Dijkstra a partir de s e devolve quantos vértices foram alcançados (incluindo s).
        int executar(int s) {
            // Limpa só o que a busca anterior usou
            for (int i = 0; i < alcancadosAnterior; i++) {
                int v = ordem[i];
                dist[v] = Double.POSITIVE_INFINITY;
                sigma[v] = 0;
                posicao[v] = -1;
            }

            int alcancados = 0;
            tamanhoHeap = 0;
            dist[s] = 0;
            sigma[s] = 1;
            inserir(s);

            while (tamanhoHeap > 0) {
                int v = removerMenor();
                posicao[v] = -2;
                indiceOrdem[v] = alcancados;
                ordem[alcancados++] = v;

                for (int e = rede.inicio[v]; e < rede.inicio[v + 1]; e++) {
                    int w = rede.destino[e];
                    if (posicao[w] == -2) {
                        continue;
                    }
                    double alt = dist[v] + rede.peso[e];
                    if (alt < dist[w]) {
                        // Achou um caminho menor: os caminhos até w passam a ser os que chegam em v
                        dist[w] = alt;
                        sigma[w] = sigma[v];
                        if (posicao[w] == -1) {
                            inserir(w);
                        } else {
                            subir(posicao[w]);
                        }
                    } else if (alt == dist[w]) {
                        // Outro caminho com o mesmo tamanho: soma a quantidade de caminhos
                        sigma[w] += sigma[v];
                    }
                }
            }
            alcancadosAnterior = alcancados;
            return alcancados;
        }

        private void inserir(int v) {
            heap[tamanhoHeap] = v;
            posicao[v] = tamanhoHeap;
            tamanhoHeap++;
            subir(tamanhoHeap - 1);
        }

        private int removerMenor() {
            int menor = heap[0];
            tamanhoHeap--;
            if (tamanhoHeap > 0) {
                heap[0] = heap[tamanhoHeap];
                posicao[heap[0]] = 0;
                descer(0);
            }
            return menor;
        }

        private void subir(int i) {
            int v = heap[i];
            while (i > 0) {
                int pai = (i - 1) / 2;
                if (dist[heap[pai]] <= dist[v]) {
                    break;
                }
                heap[i] = heap[pai];
                posicao[heap[i]] = i;
                i = pai;
            }
            heap[i] = v;
            posicao[v] = i;
        }

        private void descer(int i) {
            int v = heap[i];
            while (true) {
                int filho = 2 * i + 1;
                if (filho >= tamanhoHeap) {
                    break;
                }
                if (filho + 1 < tamanhoHeap && dist[heap[filho + 1]] < dist[heap[filho]]) {
                    filho++;
                }
                if (dist[heap[filho]] >= dist[v]) {
                    break;
                }
                heap[i] = heap[filho];
                posicao[heap[i]] = i;
                i = filho;
            }
            heap[i] = v;
            posicao[v] = i;
        }
    }
}
