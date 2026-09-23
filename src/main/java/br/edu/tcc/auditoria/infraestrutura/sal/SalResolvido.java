package br.edu.tcc.auditoria.infraestrutura.sal;

import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import java.nio.file.Path;
import java.util.Optional;

// Representa o sal em uso nesta subida e de onde ele veio. O arquivo vem vazio quando o sal veio de propriedade ou variável, e a restrição vem vazia quando nada foi gravado.
public record SalResolvido(
        SalDeInstalacao sal,
        OrigemDoSal origem,
        Optional<Path> arquivo,
        Optional<ArquivoDeSalLocal.Restricao> restricao) {

    // Valida que haja sal e origem, e que arquivo e restrição sejam Optional, nunca nulos.
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

    // Retorna a impressão digital do sal em uso, e nunca o sal.
    public ImpressaoDigitalDoSal impressaoDigital() {
        return ImpressaoDigitalDoSal.de(sal);
    }
}
