package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Tradução entre apontamento do domínio e linha gravada.
 *
 * <p>Dois pontos merecem atenção:</p>
 *
 * <ul>
 *   <li><strong>{@code OrigemEvidencia} é um tipo selado de três variantes</strong>
 *       com formatos diferentes, e o banco guarda três colunas achatadas. O
 *       {@code switch} de padrões abaixo cobre as três; se uma quarta variante
 *       surgir, o compilador reclama aqui em vez de o sistema gravar origem
 *       incompleta em silêncio.</li>
 *   <li><strong>{@code ValorEmRisco} nunca vira coluna vazia sem explicação.</strong>
 *       Ou grava o montante, ou grava o motivo de não haver montante, e o banco
 *       recusa qualquer outra combinação.</li>
 * </ul>
 */
final class MapeadorDeAchado {

    static final String ORIGEM_DO_DOCUMENTO = "DO_DOCUMENTO";
    static final String ORIGEM_DE_TABELA_NORMATIVA = "DE_TABELA_NORMATIVA";
    static final String ORIGEM_DA_REGRA = "DA_REGRA";

    private MapeadorDeAchado() {
    }

    static List<EvidenciaEmbutida> paraEntidade(List<Evidencia> evidencias) {
        return evidencias.stream().map(MapeadorDeAchado::paraEntidade).toList();
    }

    static EvidenciaEmbutida paraEntidade(Evidencia evidencia) {
        return switch (evidencia.origem()) {
            case OrigemEvidencia.DoDocumento origem -> montar(
                    evidencia, ORIGEM_DO_DOCUMENTO, origem.localizacao(), null);
            case OrigemEvidencia.DeTabelaNormativa origem -> montar(
                    evidencia, ORIGEM_DE_TABELA_NORMATIVA, origem.nomeTabela(), origem.versaoTabela());
            case OrigemEvidencia.DaRegra origem -> montar(
                    evidencia, ORIGEM_DA_REGRA, origem.descricao(), null);
        };
    }

    static Achado paraDominio(AchadoEntidade entidade) {
        return new Achado(
                entidade.regraId(),
                entidade.regraVersao(),
                entidade.severidade(),
                new ChaveAcesso(entidade.chaveAcesso()),
                OptionalInt.of(entidade.numeroItem()),
                entidade.evidencias().stream().map(MapeadorDeAchado::paraDominio).toList(),
                entidade.fundamentoNormativo(),
                new PeriodoVigencia(
                        entidade.vigenciaInicio(), Optional.ofNullable(entidade.vigenciaFim())),
                valorEmRisco(entidade));
    }

    static Evidencia paraDominio(EvidenciaEmbutida embutida) {
        return new Evidencia(
                embutida.campoAnalisado(),
                Optional.ofNullable(embutida.valorEncontrado()),
                Optional.ofNullable(embutida.valorEsperado()),
                origem(embutida));
    }

    private static OrigemEvidencia origem(EvidenciaEmbutida embutida) {
        return switch (embutida.origemTipo()) {
            case ORIGEM_DO_DOCUMENTO -> new OrigemEvidencia.DoDocumento(embutida.origemPrimeiroTermo());
            case ORIGEM_DE_TABELA_NORMATIVA -> new OrigemEvidencia.DeTabelaNormativa(
                    embutida.origemPrimeiroTermo(), embutida.origemSegundoTermo());
            case ORIGEM_DA_REGRA -> new OrigemEvidencia.DaRegra(embutida.origemPrimeiroTermo());
            default -> throw new PersistenciaInconsistente(
                    ("A evidência gravada diz ter origem \"%s\", que não corresponde a nenhuma variante "
                            + "conhecida. A linha foi gravada por outra versão do sistema ou alterada "
                            + "fora dele.").formatted(embutida.origemTipo()));
        };
    }

    private static ValorEmRisco valorEmRisco(AchadoEntidade entidade) {
        if (entidade.valorEmRisco() != null) {
            return ValorEmRisco.calculado(entidade.valorEmRisco());
        }
        if (entidade.motivoValorAusente() == null) {
            throw new PersistenciaInconsistente(
                    ("O apontamento %s não tem valor em risco nem motivo para não ter. A restrição do "
                            + "banco impede essa combinação; a linha veio de fora do sistema.")
                            .formatted(entidade.id()));
        }
        return ValorEmRisco.naoCalculavel(entidade.motivoValorAusente());
    }

    private static EvidenciaEmbutida montar(
            Evidencia evidencia, String tipo, String primeiroTermo, String segundoTermo) {
        return new EvidenciaEmbutida(
                evidencia.campoAnalisado(),
                evidencia.valorEncontrado().orElse(null),
                evidencia.valorEsperado().orElse(null),
                tipo,
                primeiroTermo,
                segundoTermo);
    }
}
