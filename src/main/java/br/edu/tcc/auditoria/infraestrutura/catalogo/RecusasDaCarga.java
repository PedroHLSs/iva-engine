package br.edu.tcc.auditoria.infraestrutura.catalogo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Classe que junta todos os problemas de uma importação, de todos os arquivos, para a carga ser recusada inteira numa mensagem só, com linha, coluna e valor de cada um. Nada é gravado enquanto houver um problema. Acrescentada na Etapa 12.
public final class RecusasDaCarga {

    // Início "Linha 5: " ou "Linha 5 de cobertura.csv: " que as mensagens de linha já trazem.
    private static final Pattern PREFIXO_DE_LINHA = Pattern.compile("^Linha (\\d+)(?: de [^:]+)?: ");

    private final List<LinhaRecusada> recusadas = new ArrayList<>();
    private final Set<String> problemasDeArquivoJaVistos = new LinkedHashSet<>();
    private final Set<String> arquivosComProblema = new LinkedHashSet<>();

    // Registra a recusa que veio do leitor, de um importador ou do domínio, no arquivo informado.
    public void registrar(String arquivo, RuntimeException recusa) {
        registrar(arquivo, recusa, Optional.empty(), Optional.empty());
    }

    // Registra a recusa dizendo a coluna e o valor, para quando quem lança não sabe dizê-los.
    public void registrar(
            String arquivo, RuntimeException recusa, Optional<String> coluna, Optional<String> valor) {
        String mensagem = recusa.getMessage() == null ? recusa.getClass().getSimpleName() : recusa.getMessage();

        if (recusa instanceof RecusaDeCampo deCampo) {
            adicionar(new LinhaRecusada(arquivo, Optional.of(deCampo.linha()),
                    Optional.ofNullable(deCampo.coluna()), Optional.ofNullable(deCampo.valor()),
                    semPrefixo(mensagem)));
            return;
        }
        if (recusa instanceof RecusaDeCabecalho) {
            registrarDoArquivo(arquivo, semPrefixo(mensagem));
            return;
        }
        Matcher prefixo = PREFIXO_DE_LINHA.matcher(mensagem);
        if (prefixo.find()) {
            adicionar(new LinhaRecusada(arquivo, Optional.of(Integer.parseInt(prefixo.group(1))),
                    coluna, valor, mensagem.substring(prefixo.end())));
            return;
        }
        registrarDoArquivo(arquivo, mensagem);
    }

    // Registra um problema de uma linha, sem coluna.
    public void registrarDaLinha(String arquivo, int linha, Optional<String> coluna,
            Optional<String> valor, String motivo) {
        adicionar(new LinhaRecusada(arquivo, Optional.of(linha), coluna, valor, motivo));
    }

    // Registra um problema do arquivo inteiro, uma vez só, mesmo que apareça em toda linha.
    public void registrarDoArquivo(String arquivo, String motivo) {
        if (problemasDeArquivoJaVistos.add(arquivo + "\u0000" + motivo)) {
            adicionar(new LinhaRecusada(arquivo, Optional.empty(), Optional.empty(), Optional.empty(), motivo));
        }
    }

    // Diz se o arquivo já tem algum problema registrado.
    public boolean temRecusaEm(String arquivo) {
        return arquivosComProblema.contains(arquivo);
    }

    // Devolve os problemas na ordem em que foram encontrados.
    public List<LinhaRecusada> recusadas() {
        return List.copyOf(recusadas);
    }

    // Recusa a carga inteira, com todos os problemas numa mensagem só, se houver algum.
    public void lancarSeHouver() {
        if (recusadas.isEmpty()) {
            return;
        }
        StringBuilder mensagem = new StringBuilder()
                .append("A carga foi recusada inteira: ")
                .append(recusadas.size()).append(" problema(s) em ")
                .append(arquivosComProblema.size()).append(" arquivo(s). Nada foi gravado; corrija todos ")
                .append("e envie de novo.");
        for (LinhaRecusada recusada : recusadas) {
            mensagem.append(System.lineSeparator()).append("  - ").append(recusada.comoTexto());
        }
        throw new CargaRecusada(mensagem.toString(), recusadas);
    }

    // Método auxiliar que guarda o problema e marca o arquivo.
    private void adicionar(LinhaRecusada recusada) {
        recusadas.add(recusada);
        arquivosComProblema.add(recusada.arquivo());
    }

    // Método auxiliar que tira o "Linha N: " do começo da mensagem, porque a linha já sai à parte.
    private static String semPrefixo(String mensagem) {
        Matcher prefixo = PREFIXO_DE_LINHA.matcher(mensagem);
        return prefixo.find() ? mensagem.substring(prefixo.end()) : mensagem;
    }
}
