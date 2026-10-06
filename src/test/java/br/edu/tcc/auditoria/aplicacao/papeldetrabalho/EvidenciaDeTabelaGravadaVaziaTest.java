package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.Uf;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Correção de 04/10/2026 (D025): execução gravada antes da correção de R01 e R06 tem a evidência do lado da tabela vazia no banco. Quando a nota informou aquele mesmo campo, o vazio não pode ser "não informado": é a tabela que não tem registro, e a planilha diz isso. O caso da R07 — o campo que a nota não trouxe — continua vazio, e continua "(não informado)". Nenhuma regra é reconhecida pelo identificador. Valores fictícios.
class EvidenciaDeTabelaGravadaVaziaTest {

    private static final Instant AGORA = Instant.parse("1900-01-01T00:00:00Z");
    private static final String CHAVE = "1".repeat(44);
    private static final String CODIGO = "999999";

    // O formato de R01 e R06 antes da correção: o código na nota, e o mesmo campo vazio do lado da tabela.
    @Test
    void ladoDaTabelaVazioDeCampoQueANotaInformouDeveDizerQueNaoHaRegistro() {
        LinhaDeAchado linha = linhaCom(List.of(
                doDocumento("cClassTrib", CODIGO),
                daTabela("cClassTrib")));

        assertThat(linha.valoresEncontrados())
                .containsExactly(Optional.of(CODIGO), Optional.of(Evidencia.NENHUM_REGISTRO_NA_TABELA));
    }

    // O formato da R07: o campo exigido não veio na nota, e o vazio quer dizer exatamente isso.
    @Test
    void campoQueANotaNaoTrouxeDeveContinuarVazio() {
        LinhaDeAchado linha = linhaCom(List.of(
                doDocumento("cClassTrib", CODIGO),
                daTabela("campoExigidoFicticio")));

        assertThat(linha.valoresEncontrados()).containsExactly(Optional.of(CODIGO), Optional.empty());
    }

    private static LinhaDeAchado linhaCom(List<Evidencia> evidencias) {
        Achado achado = new Achado("RXX", "0.0.0-ficticia", Severidade.CRITICA, new ChaveAcesso(CHAVE),
                OptionalInt.of(1), evidencias, "FUNDAMENTO FICTICIO PARA TESTE",
                PeriodoVigencia.aPartirDe(LocalDate.of(1900, 1, 1)), ValorEmRisco.naoCalculavel("motivo fictício"));
        AchadoRegistrado registrado = new AchadoRegistrado(UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                achado, new HashDoItem("e".repeat(64)), Optional.empty(), AGORA, AGORA);
        ExecucaoAuditoria execucao = ExecucaoAuditoria.de(UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                AGORA, "a".repeat(64), "catalogo-ficticio", "0.0-ficticia", 1, 1, List.of("RXX"), List.of(achado));

        PapelDeTrabalho papel = new MontadorDePapelDeTrabalho(
                execucaoId -> List.of(registrado),
                execucaoId -> List.of(),
                chaves -> Map.of(new ChaveAcesso(CHAVE), new DadosDoDocumento(
                        new ChaveAcesso(CHAVE), "99", "999", "111111", LocalDate.of(1900, 6, 15), Uf.SP)),
                chave -> new IdentificadorPseudonimizado("f".repeat(64)),
                execucaoId -> Optional.of(List.of()), execucaoId -> Optional.of(0),
                versao -> NaturezaDaCarga.naoDeclarada(), execucaoId -> Optional.empty())
                .montar(execucao);
        return papel.achados().get(0);
    }

    private static Evidencia doDocumento(String campo, String valor) {
        return new Evidencia(campo, Optional.of(valor), Optional.empty(), new OrigemEvidencia.DoDocumento("item 1"));
    }

    private static Evidencia daTabela(String campo) {
        return new Evidencia(campo, Optional.empty(), Optional.empty(),
                new OrigemEvidencia.DeTabelaNormativa("tabela-ficticia", "FONTE FICTICIA v0.0"));
    }
}
