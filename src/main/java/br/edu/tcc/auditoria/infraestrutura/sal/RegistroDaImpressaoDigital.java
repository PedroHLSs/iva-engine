package br.edu.tcc.auditoria.infraestrutura.sal;

import java.util.Optional;

// Interface do registro da impressão digital do sal que produziu o que está gravado. É implementada em persistencia, para o guarda de subida ser testado sem banco.
public interface RegistroDaImpressaoDigital {

    // Retorna a impressão digital gravada, se já houver uma.
    Optional<Registro> registrada();

    // Grava, ou substitui, a impressão digital da instalação.
    void registrar(ImpressaoDigitalDoSal impressao, OrigemDoSal origem, boolean sobreAcervoExistente);

    // Representa o que está gravado. sobreAcervoExistente diz que o registro foi feito sobre um banco que já tinha documentos, sem nada para conferir, para o diagnóstico não afirmar uma checagem que não houve.
    record Registro(ImpressaoDigitalDoSal impressao, OrigemDoSal origem, boolean sobreAcervoExistente) {
    }
}
