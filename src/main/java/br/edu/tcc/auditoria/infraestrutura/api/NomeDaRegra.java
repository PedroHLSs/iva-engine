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

// Representa o nome de uma regra por extenso, para a tela não mostrar só o código, que continua indo junto. Cada nome resume a pergunta que a regra faz, sem valor normativo, e regra que a tabela não conhece vem com nome null e o motivo.
record NomeDaRegra(String nome, String motivoDaAusencia) {

    // Tabela de nomes, com a constante ID de cada regra como chave. O NomeDaRegraTest confere que toda regra do conjunto padrão tem nome.
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

    // Valida que haja nome ou motivo, e não os dois.
    NomeDaRegra {
        exigirPar(nome, motivoDaAusencia, "(sem identificador)");
    }

    // Método estático que devolve o nome da regra, ou a falta dele com o motivo.
    static NomeDaRegra de(String regraId) {
        String nome = NOMES.get(regraId);
        if (nome != null) {
            return new NomeDaRegra(nome, null);
        }
        return new NomeDaRegra(null,
                ("esta versão do sistema não tem nome para a regra %s: ela não está no conjunto de "
                        + "regras que o código de hoje monta").formatted(regraId));
    }

    // Método estático que confere o par nome e motivo; fica aqui para os cinco DTOs recusarem do mesmo jeito.
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

    // Método estático que devolve os códigos que têm nome, para o teste comparar com o conjunto padrão.
    static Set<String> identificadoresComNome() {
        return NOMES.keySet();
    }
}
