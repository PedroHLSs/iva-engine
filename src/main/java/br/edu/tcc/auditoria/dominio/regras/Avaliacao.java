package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.excecao.AvaliacaoInvalida;

import java.util.Optional;
import java.util.OptionalInt;

// Guarda o resultado de uma regra sobre um item. São três tipos: achou problema, não achou problema, ou não conseguiu avaliar.
public sealed interface Avaliacao {

    // Devolve qual regra fez esta avaliação.
    String regraId();

    // Devolve a versão da regra, para ser possível refazer o relatório igual.
    String regraVersao();

    // Devolve a chave de acesso da nota avaliada.
    ChaveAcesso chaveAcesso();

    // Devolve o número do item avaliado, ou vazio se a avaliação for da nota inteira.
    OptionalInt numeroItem();

    // Devolve o resultado: ACHADO, CONFORME ou NAO_AVALIADO.
    ResultadoAvaliacao resultado();

    // Devolve o apontamento, se a regra achou problema; nos outros casos, vazio.
    Optional<Achado> achado();

    // Devolve o motivo de a regra não ter conseguido avaliar; nos outros casos, vazio.
    Optional<String> motivoDaNaoAvaliacao();

    // Cria o resultado de quando a regra achou problema.
    static Avaliacao comAchado(Achado apontamento) {
        return new ComAchado(apontamento);
    }

    // Cria o resultado de quando a regra rodou inteira e não achou problema.
    static Avaliacao conforme(
            String regraId, String regraVersao, ChaveAcesso chaveAcesso, OptionalInt numeroItem) {
        return new Conforme(regraId, regraVersao, chaveAcesso, numeroItem);
    }

    // Cria o resultado de quando a regra não conseguiu avaliar, junto com o motivo.
    static Avaliacao naoAvaliada(
            String regraId, String regraVersao, ChaveAcesso chaveAcesso, OptionalInt numeroItem, String motivo) {
        return new NaoAvaliada(regraId, regraVersao, chaveAcesso, numeroItem, motivo);
    }

    // Resultado de quando a regra achou problema; os dados da regra, da nota e do item vêm do próprio apontamento.
    record ComAchado(Achado apontamento) implements Avaliacao {

        // Não deixa criar este resultado sem o apontamento.
        public ComAchado {
            if (apontamento == null) {
                throw new AvaliacaoInvalida(
                        "Uma avaliação com achado precisa do achado. "
                                + "Para regra que nada encontrou use Avaliacao.conforme(...), e para regra "
                                + "que não pôde julgar use Avaliacao.naoAvaliada(..., motivo).");
            }
        }

        @Override
        public String regraId() {
            return apontamento.regraId();
        }

        @Override
        public String regraVersao() {
            return apontamento.regraVersao();
        }

        @Override
        public ChaveAcesso chaveAcesso() {
            return apontamento.chaveAcesso();
        }

        @Override
        public OptionalInt numeroItem() {
            return apontamento.numeroItem();
        }

        @Override
        public ResultadoAvaliacao resultado() {
            return ResultadoAvaliacao.ACHADO;
        }

        @Override
        public Optional<Achado> achado() {
            return Optional.of(apontamento);
        }

        @Override
        public Optional<String> motivoDaNaoAvaliacao() {
            return Optional.empty();
        }
    }

    // Resultado de quando a regra rodou inteira e não achou problema.
    record Conforme(String regraId, String regraVersao, ChaveAcesso chaveAcesso, OptionalInt numeroItem)
            implements Avaliacao {

        // Confere se regra, versão, nota e item foram informados.
        public Conforme {
            ValidacaoDeAvaliacao.exigirIdentificacao(regraId, regraVersao, chaveAcesso, numeroItem);
        }

        @Override
        public ResultadoAvaliacao resultado() {
            return ResultadoAvaliacao.CONFORME;
        }

        @Override
        public Optional<Achado> achado() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaNaoAvaliacao() {
            return Optional.empty();
        }
    }

    // Resultado de quando a regra não conseguiu avaliar. O motivo é obrigatório, senão quem lê pode achar que estava tudo certo.
    record NaoAvaliada(
            String regraId,
            String regraVersao,
            ChaveAcesso chaveAcesso,
            OptionalInt numeroItem,
            String motivo) implements Avaliacao {

        // Confere regra, versão, nota e item, e exige o motivo.
        public NaoAvaliada {
            ValidacaoDeAvaliacao.exigirIdentificacao(regraId, regraVersao, chaveAcesso, numeroItem);
            if (motivo == null || motivo.isBlank()) {
                throw new AvaliacaoInvalida(
                        "Uma avaliação não concluída precisa registrar o motivo: ausência de dado e "
                                + "conformidade não podem se parecer na saída.");
            }
        }

        @Override
        public ResultadoAvaliacao resultado() {
            return ResultadoAvaliacao.NAO_AVALIADO;
        }

        @Override
        public Optional<Achado> achado() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaNaoAvaliacao() {
            return Optional.of(motivo);
        }
    }

}
