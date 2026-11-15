import java.io.IOException;
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
                    case "6" -> naoImplementado("Verificar se o grafo é conexo");
                    case "7" -> naoImplementado("Mostrar componentes (fracamente conectados)");
                    case "8" -> naoImplementado("Verificar se o grafo é Euleriano");
                    case "9" -> naoImplementado("Verificar se o grafo é cíclico");
                    case "10" -> naoImplementado("Centralidade de proximidade");
                    case "11" -> naoImplementado("Centralidade de intermediação");
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
