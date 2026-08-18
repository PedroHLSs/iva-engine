package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R07 — os campos que o catálogo marca como obrigatórios para aquele
 * {@code cClassTrib} vieram preenchidos no item?
 *
 * <p>Quais campos cada código exige é conteúdo normativo e chega na coluna
 * {@code camposObrigatoriosCondicionados} da tabela importada. A regra lê essa
 * lista e confere, campo a campo, se o item trouxe o dado. Nenhuma exigência
 * está escrita aqui.</p>
 *
 * <p>Preenchido significa ter vindo. Um campo declarado com valor zero está
 * preenchido; um campo ausente não está. Confundir os dois apagaria justamente o
 * que esta regra existe para detectar — ver {@link CampoDoItem}.</p>
 *
 * <p>Severidade crítica: campo estruturalmente necessário ausente deixa o grupo
 * de IBS/CBS do item sem como ser interpretado.</p>
 *
 * <h2>Nome que o vocabulário não reconhece</h2>
 *
 * <p>O catálogo pode citar um campo que este sistema não sabe ler. Nesse caso a
 * regra não tem como conferir aquela exigência, e não finge que conferiu: se não
 * houver nenhum campo conhecido faltando, o resultado é {@code NAO_AVALIADO}
 * citando os nomes não reconhecidos. Se houver campo conhecido faltando, o
 * achado sai — a falta é fato — e os nomes não reconhecidos entram como
 * evidência, para que não desapareçam do relatório.</p>
 *
 * <h2>Lista vazia</h2>
 *
 * <p>Registro encontrado e lista vazia é resposta completa do catálogo: aquele
 * código não condiciona campo nenhum. O resultado é {@code CONFORME}. Difere do
 * conjunto vazio de CSTs em R02, onde a lista vazia deixaria a pergunta sem
 * referência alguma; aqui "nenhum campo exigido" é uma exigência satisfeita por
 * qualquer item.</p>
 */
public final class RegraCamposObrigatoriosPreenchidos extends RegraDeItem {

    public static final String ID = "R07";
    public static final String VERSAO = "1.0.0";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String versao() {
        return VERSAO;
    }

    @Override
    public Severidade severidade() {
        return Severidade.CRITICA;
    }

    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<CodigoClassificacaoTributaria> codigo = item.codigoClassificacaoTributaria();
        if (codigo.isEmpty()) {
            return naoAvaliada(item, documento,
                    "O item não declarou cClassTrib; sem ele o catálogo não tem como dizer que campos são exigidos.");
        }

        Optional<ClassificacaoTributaria> registro = contexto.classificacaoTributaria(codigo.get());
        if (registro.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O catálogo nada diz sobre o cClassTrib \"%s\" na data de emissão, então não há lista "
                            + "de campos exigidos a conferir.").formatted(codigo.get().valor()));
        }

        ClassificacaoTributaria classificacao = registro.get();
        List<String> exigidos = classificacao.camposObrigatoriosCondicionados();
        if (exigidos.isEmpty()) {
            return conforme(item, documento);
        }

        List<String> ausentes = new ArrayList<>();
        List<String> naoReconhecidos = new ArrayList<>();
        for (String nome : exigidos) {
            Optional<CampoDoItem> campo = CampoDoItem.porNome(nome);
            if (campo.isEmpty()) {
                naoReconhecidos.add(nome);
            } else if (!campo.get().estaPreenchidoEm(item)) {
                ausentes.add(nome);
            }
        }

        if (ausentes.isEmpty()) {
            if (naoReconhecidos.isEmpty()) {
                return conforme(item, documento);
            }
            return naoAvaliada(item, documento, motivoDeNomesNaoReconhecidos(codigo.get(), naoReconhecidos));
        }

        List<Evidencia> evidencias = new ArrayList<>();
        evidencias.add(doDocumento("cClassTrib", item, codigo.get().valor()));
        for (String nome : ausentes) {
            // valorEncontrado vazio é o registro de que o campo não veio, e
            // valorEsperado vazio porque a exigência é de presença, não de valor.
            evidencias.add(daTabela(
                    nome,
                    RegraClassificacaoTributariaExiste.TABELA,
                    classificacao.fonteNormativa(),
                    Optional.empty(),
                    Optional.empty()));
        }
        if (!naoReconhecidos.isEmpty()) {
            evidencias.add(daTabela(
                    "camposObrigatoriosCondicionados",
                    RegraClassificacaoTributariaExiste.TABELA,
                    classificacao.fonteNormativa(),
                    Optional.of(String.join(", ", naoReconhecidos)),
                    Optional.empty()));
        }

        return comAchado(
                item,
                documento,
                List.copyOf(evidencias),
                classificacao.dispositivoLegal(),
                classificacao.vigencia(),
                ValorEmRisco.naoCalculavel(
                        "Campo exigido e não informado impede o cálculo: não há o que quantificar sem o dado."));
    }

    private static String motivoDeNomesNaoReconhecidos(
            CodigoClassificacaoTributaria codigo, List<String> naoReconhecidos) {

        return ("O catálogo exige, para o cClassTrib \"%s\", campos que este sistema não sabe ler: %s. "
                + "Os campos reconhecidos vieram preenchidos, mas a exigência não foi conferida por inteiro. "
                + "Nomes aceitos são os dos campos do item, como \"%s\".").formatted(
                codigo.valor(),
                String.join(", ", naoReconhecidos),
                CampoDoItem.BASE_CALCULO_IBS.nomeNoCatalogo());
    }
}
