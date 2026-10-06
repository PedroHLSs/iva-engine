package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ClassificacaoDoCatalogo;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// D015 (03/10/2026): a resposta separa "a carga não declarou os campos exigidos" de "a carga declarou NENHUM". Até essa data as duas saíam com a mesma frase. Valores fictícios.
class MotivoDosCamposExpostoTest {

    @Test
    void celulaEmBrancoDeveSairComOMotivoDeNaoDeclarado() {
        TratamentoExposto.ClassificacaoExposta exposta = expor(Optional.empty());

        assertThat(exposta.camposObrigatoriosCondicionados()).isEmpty();
        assertThat(exposta.motivoSemCampoCondicionado())
                .isEqualTo(TratamentoExposto.ClassificacaoExposta.CAMPOS_NAO_DECLARADOS);
    }

    @Test
    void nenhumDeveSairComOMotivoDeDeclaradoNenhum() {
        TratamentoExposto.ClassificacaoExposta exposta = expor(Optional.of(List.of()));

        assertThat(exposta.camposObrigatoriosCondicionados()).isEmpty();
        assertThat(exposta.motivoSemCampoCondicionado())
                .isEqualTo(TratamentoExposto.ClassificacaoExposta.SEM_CAMPO_CONDICIONADO);
    }

    @Test
    void listaDeveSairSemMotivo() {
        TratamentoExposto.ClassificacaoExposta exposta = expor(Optional.of(List.of("valorCbs")));

        assertThat(exposta.camposObrigatoriosCondicionados()).containsExactly("valorCbs");
        assertThat(exposta.motivoSemCampoCondicionado()).isNull();
    }

    // Método auxiliar que monta a classificação fictícia e a converte pelo mesmo caminho da resposta.
    private static TratamentoExposto.ClassificacaoExposta expor(Optional<List<String>> campos) {
        ClassificacaoTributaria registro = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria("XXX001"),
                Set.of(new CodigoCst("AAA")),
                "DISPOSITIVO FICTICIO PARA TESTE",
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                campos,
                ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0"));
        return TratamentoExposto.ClassificacaoExposta.de(ClassificacaoDoCatalogo.de(registro));
    }
}
