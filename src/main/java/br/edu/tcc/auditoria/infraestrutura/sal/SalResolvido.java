package br.edu.tcc.auditoria.infraestrutura.sal;

import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import java.nio.file.Path;
import java.util.Optional;

/**
 * O sal em uso nesta subida, com a procedência dele.
 *
 * <p>Carrega a origem junto porque o sal sozinho não responde à pergunta que o
 * diagnóstico precisa responder. Dois sais idênticos vindos de lugares
 * diferentes contam histórias diferentes sobre a instalação.</p>
 *
 * @param arquivo   o arquivo local, quando a origem envolveu arquivo; vazio
 *                  quando o sal veio de propriedade ou de variável de ambiente
 * @param restricao o que se conseguiu restringir de permissão ao gravar; vazio
 *                  quando nada foi gravado nesta subida
 */
public record SalResolvido(
        SalDeInstalacao sal,
        OrigemDoSal origem,
        Optional<Path> arquivo,
        Optional<ArquivoDeSalLocal.Restricao> restricao) {

    public SalResolvido {
        if (sal == null) {
            throw new SalTrocado("O sal resolvido precisa do sal.");
        }
        if (origem == null) {
            throw new SalTrocado(
                    "O sal resolvido precisa dizer de onde veio: sem isso o diagnóstico não responde "
                            + "por que a impressão digital mudou.");
        }
        if (arquivo == null || restricao == null) {
            throw new SalTrocado(
                    "Ausência de arquivo ou de restrição se representa com Optional.empty(), nunca "
                            + "com nulo.");
        }
    }

    /** A impressão digital do sal em uso. Nunca o sal. */
    public ImpressaoDigitalDoSal impressaoDigital() {
        return ImpressaoDigitalDoSal.de(sal);
    }
}
