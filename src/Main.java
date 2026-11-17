import java.io.IOException;
import java.util.Scanner;

/**
 * Menu principal da aplicação.
 *
 * As opções 6 a 11 são das outras pessoas da equipe: basta trocar o
 * "naoImplementado(...)" pela chamada da função correspondente, usando o
 * grafo carregado em 'grafo'.
 */
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Menu principal da aplicação.
 *
 * As opções 6 a 11 são das outras pessoas da equipe: basta trocar o
 * "naoImplementado(...)" pela chamada da função correspondente, usando o
 * grafo carregado em 'grafo'.
 */
public class Main {

    private static final String NOS_PADRAO = "nos.csv";
    private static final String ARESTAS_PADRAO = "arestas.csv";
    private static final String PAJEK_PADRAO = "grafo.net";

    // Centralidades: até quantos vértices mostra tudo, quantos mostra no grafo grande
    // e a partir de quantos vértices estima o tempo da intermediação antes de calcular
    private static final int MOSTRAR_TODOS = 20;
    private static final int TOP_CENTRALIDADE = 10;
    private static final int LIMITE_ESTIMATIVA = 2000;
    private static final int AMOSTRA_ESTIMATIVA = 200;

    private static final Scanner entrada = new Scanner(System.in);
    private static Grafo grafo = null;

    public static void main(String[] args) {
        while (true) {
            mostrarMenu();
            String opcao = entrada.nextLine().strip();
            try {
                switch (opcao) {
                    case "1" -> carregarCSV();
                    case "2" -> carregarPajek();
                    case "3" -> salvarPajek();
                    case "4" -> gerarAleatorio();
                    case "5" -> mostrarResumo();
                    case "6" -> verificarConexo();
                    case "7" -> mostrarComponentes();
                    case "8" -> verificarEuleriano();
                    case "9" -> verificarCiclico();
                    case "10" -> calcularProximidade();
                    case "11" -> calcularIntermediacao();
                    case "0" -> {
                        System.out.println("Saindo.");
                        return;
                    }
                    default -> System.out.println("Opção inválida.");
                }
            } catch (IOException e) {
                System.out.println("Erro de arquivo: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Erro: " + e.getMessage());
            }
        }
    }

    private static void mostrarMenu() {
        System.out.println();
        System.out.println("=========== GRAFOS - MALHA VIÁRIA ===========");
        System.out.println("Grafo atual: " + (grafo == null ? "nenhum"
                : grafo.getNumVertices() + " vértices, " + grafo.getNumArestas() + " arestas"
                + (grafo.isDirecionado() ? " (direcionado)" : " (não direcionado)")));
        System.out.println(" 1 - Carregar malha viária (nos.csv + arestas.csv)");
        System.out.println(" 2 - Carregar grafo de arquivo Pajek");
        System.out.println(" 3 - Gravar grafo em arquivo Pajek");
        System.out.println(" 4 - Gerar grafo aleatório");
        System.out.println(" 5 - Resumo do grafo");
        System.out.println(" 6 - Verificar se é conexo");
        System.out.println(" 7 - Mostrar componentes");
        System.out.println(" 8 - Verificar se é Euleriano");
        System.out.println(" 9 - Verificar se é cíclico");
        System.out.println("10 - Centralidade de proximidade");
        System.out.println("11 - Centralidade de intermediação");
        System.out.println(" 0 - Sair");
        System.out.print("Opção: ");
    }

    // ------------------------------------------------------------------ opções

    private static void carregarCSV() throws IOException {
        String nos = lerTexto("Arquivo de nós", NOS_PADRAO);
        String arestas = lerTexto("Arquivo de arestas", ARESTAS_PADRAO);
        long ini = System.currentTimeMillis();
        grafo = LeitorCSV.carregar(nos, arestas);
        System.out.printf("Malha carregada em %d ms.%n", System.currentTimeMillis() - ini);
        System.out.println(grafo.resumo());
    }

    private static void carregarPajek() throws IOException {
        String caminho = lerTexto("Arquivo Pajek", PAJEK_PADRAO);
        long ini = System.currentTimeMillis();
        grafo = Pajek.carregar(caminho);
        System.out.printf("Grafo carregado em %d ms.%n", System.currentTimeMillis() - ini);
        System.out.println(grafo.resumo());
    }

    private static void salvarPajek() throws IOException {
        if (!temGrafo()) {
            return;
        }
        String caminho = lerTexto("Arquivo Pajek de saída", PAJEK_PADRAO);
        Pajek.salvar(grafo, caminho);
        System.out.println("Grafo gravado em " + caminho);
    }

    private static void gerarAleatorio() {
        int n = lerInteiro("Número de nós");
        int m = lerInteiro("Número de arestas");
        boolean conexo = lerSimNao("Conexo? (s/n)");
        boolean direcionado = lerSimNao("Direcionado? (s/n)");
        grafo = GeradorAleatorio.gerar(n, m, conexo, direcionado);
        System.out.println("Grafo aleatório gerado.");
        System.out.println(grafo.resumo());
    }

    private static void mostrarResumo() {
        if (temGrafo()) {
            System.out.println(grafo.resumo());
        }
    }

    private static void verificarConexo() {
        if (!temGrafo()) {
            return;
        }
        long ini = System.currentTimeMillis();
        boolean conexo = Estrutura.ehConexo(grafo);
        long tempo = System.currentTimeMillis() - ini;
        String tipo = grafo.isDirecionado() ? " (fracamente conexo)" : "";
        System.out.printf("Conexo%s: %s (%d ms)%n", tipo, conexo ? "SIM" : "NÃO", tempo);
    }

    private static void mostrarComponentes() {
        if (!temGrafo()) {
            return;
        }
        long ini = System.currentTimeMillis();
        List<List<Integer>> comps = Estrutura.componentes(grafo);
        long tempo = System.currentTimeMillis() - ini;

        if (comps.size() <= 1) {
            System.out.printf("O grafo é conexo: possui apenas 1 componente com %d vértices (%d ms).%n",
                    grafo.getNumVertices(), tempo);
            return;
        }

        System.out.printf("O grafo é desconexo: %d componentes encontrados (%d ms).%n", comps.size(), tempo);
        for (int i = 0; i < comps.size(); i++) {
            List<Integer> c = comps.get(i);
            System.out.printf("  Componente %2d: %d vértices -> ", i + 1, c.size());
            // Mostra até os 10 primeiros vértices de cada componente para não poluir a tela em grafos gigantes
            int limite = Math.min(c.size(), 10);
            StringBuilder sb = new StringBuilder("[");
            for (int j = 0; j < limite; j++) {
                if (j > 0) {
                    sb.append(", ");
                }
                int v = c.get(j);
                sb.append(v + 1).append(" (\"").append(grafo.getRotulo(v)).append("\")");
            }
            if (c.size() > limite) {
                sb.append(", ...");
            }
            sb.append("]");
            System.out.println(sb);
        }
    }

    private static void verificarEuleriano() {
        if (!temGrafo()) {
            return;
        }
        long ini = System.currentTimeMillis();
        boolean euleriano = Estrutura.ehEuleriano(grafo);
        int desbalanceados = Estrutura.contarVerticesDesbalanceadosEuler(grafo);
        long tempo = System.currentTimeMillis() - ini;

        System.out.printf("Euleriano: %s (%d ms)%n", euleriano ? "SIM" : "NÃO", tempo);
        if (!euleriano) {
            if (desbalanceados > 0) {
                String motivo = grafo.isDirecionado()
                        ? "vértices com grau de entrada diferente do grau de saída"
                        : "vértices com grau ímpar";
                System.out.printf("Motivo: %d %s.%n", desbalanceados, motivo);
            } else {
                System.out.println("Motivo: os vértices com arestas estão divididos em componentes separados.");
            }
        }
    }

    private static void verificarCiclico() {
        if (!temGrafo()) {
            return;
        }
        long ini = System.currentTimeMillis();
        boolean ciclico = Estrutura.ehCiclico(grafo);
        long tempo = System.currentTimeMillis() - ini;
        System.out.printf("Cíclico: %s (%d ms)%n", ciclico ? "SIM" : "NÃO", tempo);
    }

    private static void calcularProximidade() throws IOException {
        if (!temGrafo()) {
            return;
        }
        long ini = System.currentTimeMillis();
        double[] proximidade = Centralidade.proximidade(grafo);
        long tempo = System.currentTimeMillis() - ini;

        System.out.printf("Centralidade de proximidade calculada para %d vértices (%d ms).%n",
                grafo.getNumVertices(), tempo);
        mostrarCentralidade(proximidade, null, "proximidade", "proximidade.csv");
    }

    private static void calcularIntermediacao() throws IOException {
        if (!temGrafo()) {
            return;
        }
        // Em grafos grandes, mede uma amostra de origens e extrapola antes de rodar o cálculo completo
        if (grafo.getNumVertices() > LIMITE_ESTIMATIVA) {
            long estimado = Centralidade.estimarTempoIntermediacao(grafo, AMOSTRA_ESTIMATIVA);
            System.out.printf("Tempo estimado (amostra de %d origens): %.1f s%n",
                    AMOSTRA_ESTIMATIVA, estimado / 1000.0);
            if (!lerSimNao("Continuar? (s/n)")) {
                return;
            }
        }
        long ini = System.currentTimeMillis();
        double[] bruto = Centralidade.intermediacao(grafo);
        long tempo = System.currentTimeMillis() - ini;
        double[] normalizado = Centralidade.normalizarIntermediacao(grafo, bruto);

        System.out.printf("Centralidade de intermediação calculada para %d vértices (%d ms).%n",
                grafo.getNumVertices(), tempo);
        mostrarCentralidade(normalizado, bruto, "intermediacao", "intermediacao.csv");
    }

    // Mostra todos os vértices em grafos pequenos ou só os maiores valores em grafos grandes,
    // e oferece gravar os valores de todos os vértices em CSV.
    // 'bruto' é opcional: quando informado, também mostra o valor sem normalizar.
    private static void mostrarCentralidade(double[] valores, double[] bruto, String nome, String arquivoPadrao)
            throws IOException {
        int n = valores.length;
        int quantidade = n <= MOSTRAR_TODOS ? n : TOP_CENTRALIDADE;
        int[] top = Centralidade.maiores(valores, quantidade);

        System.out.println(n <= MOSTRAR_TODOS ? "Valores (do maior para o menor):"
                : "Os " + quantidade + " vértices com maior " + nome + ":");
        for (int i = 0; i < top.length; i++) {
            int v = top[i];
            if (bruto == null) {
                System.out.printf("  %2d. Vértice %d (\"%s\"): %.6g%n", i + 1, v + 1, grafo.getRotulo(v), valores[v]);
            } else {
                System.out.printf("  %2d. Vértice %d (\"%s\"): %.6g (bruto: %.1f)%n",
                        i + 1, v + 1, grafo.getRotulo(v), valores[v], bruto[v]);
            }
        }

        if (lerSimNao("Gravar os valores de todos os vértices em CSV? (s/n)")) {
            String caminho = lerTexto("Arquivo de saída", arquivoPadrao);
            Centralidade.salvarCSV(grafo, bruto == null ? valores : bruto, nome, caminho);
            System.out.println("Valores gravados em " + caminho);
        }
    }

    private static void naoImplementado(String funcao) {
        if (temGrafo()) {
            System.out.println(funcao + ": ainda não implementado.");
        }
    }

    // ------------------------------------------------------------------ leitura do teclado

    private static boolean temGrafo() {
        if (grafo == null) {
            System.out.println("Nenhum grafo carregado. Use as opções 1, 2 ou 4 primeiro.");
            return false;
        }
        return true;
    }

    private static String lerTexto(String rotulo, String padrao) {
        System.out.print(rotulo + " [" + padrao + "]: ");
        String s = entrada.nextLine().strip();
        return s.isEmpty() ? padrao : s;
    }

    private static int lerInteiro(String rotulo) {
        while (true) {
            System.out.print(rotulo + ": ");
            try {
                return Integer.parseInt(entrada.nextLine().strip());
            } catch (NumberFormatException e) {
                System.out.println("Digite um número inteiro.");
            }
        }
    }

    private static boolean lerSimNao(String rotulo) {
        while (true) {
            System.out.print(rotulo + ": ");
            String s = entrada.nextLine().strip().toLowerCase();
            if (s.startsWith("s")) {
                return true;
            }
            if (s.startsWith("n")) {
                return false;
            }
            System.out.println("Responda s ou n.");
        }
    }
}
