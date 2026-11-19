// aresta da lista de adjacência: guarda destino, peso e rótulo (a origem é quem tem a lista)
public class Aresta {
    private final int destino;
    private final double peso;
    private final String rotulo;

    public Aresta(int destino, double peso, String rotulo) {
        this.destino = destino;
        this.peso = peso;
        this.rotulo = rotulo;
    }

    public int getDestino() {
        return destino;
    }

    public double getPeso() {
        return peso;
    }

    public String getRotulo() {
        return rotulo;
    }

    @Override
    public String toString() {
        return "-> " + destino + " (peso=" + peso + ", rotulo=" + rotulo + ")";
    }
}
