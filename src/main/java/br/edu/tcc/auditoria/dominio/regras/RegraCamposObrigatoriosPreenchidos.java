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

// Regra R07: os campos que o catálogo exige para esse cClassTrib vieram preenchidos? Gravidade: crítica. Campo com zero conta como preenchido; só falta quando o campo não veio.
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

    // Aplica a regra: confere cada campo exigido. Se faltar um campo conhecido, gera achado; se o catálogo usar um nome que o sistema não conhece, vira NAO_AVALIADO ou entra como evidência.
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
            // Os dois valores ficam vazios: o encontrado porque o campo não veio, e o esperado porque a regra só cobra que o campo exista.
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

    // Método auxiliar que monta a mensagem para quando o catálogo pede campos que o sistema não conhece.
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
