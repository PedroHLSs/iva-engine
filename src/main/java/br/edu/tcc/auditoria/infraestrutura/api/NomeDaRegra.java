package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.dominio.regras.RegraBeneficioExigeNcmEmAnexo;
import br.edu.tcc.auditoria.dominio.regras.RegraCamposObrigatoriosPreenchidos;
import br.edu.tcc.auditoria.dominio.regras.RegraClassificacaoTributariaExiste;
import br.edu.tcc.auditoria.dominio.regras.RegraCstCompativelComClassificacao;
import br.edu.tcc.auditoria.dominio.regras.RegraNcmExiste;
import br.edu.tcc.auditoria.dominio.regras.RegraTratamentoDeAnexoNaoAproveitado;
import br.edu.tcc.auditoria.dominio.regras.RegraValorDeTributoConfere;

import java.util.Map;
import java.util.Set;

/**
 * O nome de uma regra por extenso, para a interface não escrever só o código.
 *
 * <p>"R06" diz a quem programa qual classe rodou e não diz nada a quem lê o
 * resultado. O código continua indo junto, em todo lugar: é ele que a CLI aceita
 * em {@code --regra}, que a planilha escreve e que o gabarito de acurácia usa, e
 * sem ele na tela não há como cruzar uma coisa com a outra.</p>
 *
 * <h2>O nome não é afirmação sobre a norma</h2>
 *
 * <p>Cada nome resume a <em>pergunta</em> que a regra faz, tirada da primeira
 * linha do Javadoc da própria classe. Nenhum carrega código, percentual,
 * vínculo ou data: a resposta a cada pergunta continua sendo do catálogo
 * importado (seção 5 do CLAUDE.md).</p>
 *
 * <h2>Por que é uma tabela por identificador, e por que isso não repete o erro
 * que a {@code TraducaoDeDesfecho} evitou</h2>
 *
 * <p>Aquela classe recusou tabela por identificador porque "quem esquecesse de
 * editá-la descobriria pelo relatório". Para decidir estado, isso seria fatal.
 * Para dar nome, a tabela é inevitável — o domínio não tem onde guardá-lo, e
 * {@code dominio/regras} ficou fora desta mudança por decisão explícita —, e o
 * esquecimento não chega ao relatório: {@code NomeDaRegraTest} compara esta
 * tabela com o conjunto padrão nas duas direções, e regra nova sem nome quebra o
 * build.</p>
 *
 * <p>As chaves são as constantes {@code ID} das próprias classes, e não o texto
 * {@code "R06"} escrito de novo aqui.</p>
 *
 * <h2>Regra sem nome é ausência escrita</h2>
 *
 * <p>Uma execução gravada com um conjunto que o código de hoje não monta pode
 * trazer identificador que esta tabela não conhece. O nome vem então {@code null}
 * com o motivo ao lado — o mesmo par da D009 —, e não o código repetido no lugar
 * do nome, nem um nome deduzido do código.</p>
 *
 * <p>O nome é o do código de hoje. Enquanto as sete regras estiverem na versão
 * {@code 1.0.0}, isso coincide com o que rodou em qualquer execução gravada; a
 * versão da regra continua exposta ao lado para quando deixar de coincidir.</p>
 *
 * @param nome              o nome por extenso, ou {@code null} com o motivo
 * @param motivoDaAusencia  por que não há nome, ou {@code null} quando há
 */
record NomeDaRegra(String nome, String motivoDaAusencia) {

    private static final Map<String, String> NOMES = Map.of(
            RegraClassificacaoTributariaExiste.ID,
            "cClassTrib declarado consta do catálogo",
            RegraCstCompativelComClassificacao.ID,
            "CST compatível com o cClassTrib declarado",
            RegraBeneficioExigeNcmEmAnexo.ID,
            "Benefício declarado exige NCM vinculado a anexo",
            RegraTratamentoDeAnexoNaoAproveitado.ID,
            "NCM em anexo emitido com tributação integral",
            RegraValorDeTributoConfere.ID,
            "Valor do tributo confere com base × alíquota do catálogo",
            RegraNcmExiste.ID,
            "NCM declarado consta do catálogo",
            RegraCamposObrigatoriosPreenchidos.ID,
            "Campos exigidos pelo cClassTrib vieram preenchidos");

    NomeDaRegra {
        exigirPar(nome, motivoDaAusencia, "(sem identificador)");
    }

    /** O nome da regra, ou a ausência dele com o motivo. */
    static NomeDaRegra de(String regraId) {
        String nome = NOMES.get(regraId);
        if (nome != null) {
            return new NomeDaRegra(nome, null);
        }
        return new NomeDaRegra(null,
                ("esta versão do sistema não tem nome para a regra %s: ela não está no conjunto de "
                        + "regras que o código de hoje monta").formatted(regraId));
    }

    /**
     * A exigência do par, para os construtores dos DTOs que carregam o nome.
     *
     * <p>Mora aqui para os cinco DTOs recusarem pelo mesmo critério e com a mesma
     * frase, em vez de cada um reescrever a sua.</p>
     */
    static void exigirPar(String nome, String motivoDaAusencia, String regraId) {
        boolean semNome = nome == null || nome.isBlank();
        boolean semMotivo = motivoDaAusencia == null || motivoDaAusencia.isBlank();
        if (semNome && semMotivo) {
            throw new RespostaInvalida(
                    ("O nome da regra %s veio vazio sem dizer por quê. Sem nome e sem motivo, a tela "
                            + "voltaria a escrever só o código.").formatted(regraId));
        }
        if (!semNome && motivoDaAusencia != null) {
            throw new RespostaInvalida(
                    "O nome da regra %s não pode estar presente e ausente ao mesmo tempo."
                            .formatted(regraId));
        }
    }

    /** Os identificadores que têm nome, para o teste confrontar com o conjunto padrão. */
    static Set<String> identificadoresComNome() {
        return NOMES.keySet();
    }
}
