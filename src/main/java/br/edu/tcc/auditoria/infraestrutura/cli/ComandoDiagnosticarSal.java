package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.infraestrutura.sal.AcervoSalgado;
import br.edu.tcc.auditoria.infraestrutura.sal.ImpressaoDigitalDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.RegistroDaImpressaoDigital;
import br.edu.tcc.auditoria.infraestrutura.sal.SalResolvido;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// Classe do comando diagnosticar-sal, que mostra de onde o sal veio e a impressão digital dele, nunca o sal. Passa pelo guarda de troca de sal de propósito, porque é a ferramenta para entender a recusa.
@Component
class ComandoDiagnosticarSal implements Comando {

    static final String NOME = "diagnosticar-sal";

    private final SalResolvido salResolvido;
    private final RegistroDaImpressaoDigital registro;
    private final AcervoSalgado acervo;
    private final Saida saida;

    // Construtor que recebe o sal resolvido, o registro da impressão digital, o acervo e a saída.
    ComandoDiagnosticarSal(
            SalResolvido salResolvido,
            RegistroDaImpressaoDigital registro,
            AcervoSalgado acervo,
            Saida saida) {

        this.salResolvido = salResolvido;
        this.registro = registro;
        this.acervo = acervo;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Mostra de onde o sal foi resolvido e a impressão digital dele.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s

                  Não recebe opção nenhuma.

                  Informa a origem do sal em uso, o arquivo onde ele está quando
                  houver, a impressão digital do sal em uso e a que está gravada no
                  banco. O sal em si NÃO é mostrado.
                """.formatted(NOME);
    }

    // Mostra a origem do sal, o arquivo, a permissão e a impressão digital em uso.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of());

        ImpressaoDigitalDoSal emUso = salResolvido.impressaoDigital();

        saida.linha("Sal de pseudonimização");
        saida.linha("  origem ................. %s", salResolvido.origem().descricao());
        salResolvido.arquivo().ifPresent(caminho ->
                saida.linha("  arquivo ................ %s", caminho));
        salResolvido.restricao().ifPresent(restricao ->
                saida.linha("  permissão .............. %s", restricao.comoFoiFeita()));
        saida.linha("  impressão digital ...... %s", emUso.valor());
        saida.linhaEmBranco();

        relatarOAcervo(emUso);
    }

    // Método auxiliar que compara a impressão digital em uso com a gravada no banco e diz se conferem.
    private void relatarOAcervo(ImpressaoDigitalDoSal emUso) {
        long documentos = acervo.quantidadeDeDocumentos();
        Optional<RegistroDaImpressaoDigital.Registro> gravada = registro.registrada();

        saida.linha("Acervo");
        saida.linha("  documentos gravados .... %d", documentos);

        if (gravada.isEmpty()) {
            saida.linha("  impressão digital ...... nenhuma gravada ainda");
            saida.linhaEmBranco();
            saida.linha(documentos == 0
                    ? "Acervo vazio e sem impressão digital: o primeiro comando que auditar grava as duas coisas."
                    : "Há documentos e nenhuma impressão digital gravada. Isso acontece em acervo "
                            + "anterior a esta verificação; o próximo comando registra a impressão digital atual.");
            return;
        }

        saida.linha("  impressão digital ...... %s", gravada.get().impressao().valor());
        saida.linha("  registrada com origem .. %s", gravada.get().origem().descricao());
        saida.linhaEmBranco();

        if (gravada.get().sobreAcervoExistente()) {
            saida.linha("Ressalva: essa impressão digital foi registrada sobre um acervo que já "
                    + "existia, então não houve o que conferir na ocasião.");
            saida.linhaEmBranco();
        }

        if (gravada.get().impressao().equals(emUso)) {
            saida.linha("CONFEREM: o sal em uso é o que produziu os pseudônimos gravados.");
            return;
        }
        saida.linha("DIVERGEM: o sal em uso não é o que produziu os pseudônimos gravados.");
        if (documentos > 0) {
            saida.linha("Com %d documento(s) no banco, os comandos que tocam o acervo vão recusar "
                    + "subir. Reponha o sal anterior, ou rode \"%s --confirmo=sim\".",
                    documentos, ComandoRecomecarDoZero.NOME);
        }
    }
}
