package br.edu.tcc.auditoria.infraestrutura.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Guarda as falhas de um lote em memória, na ordem em que aconteceram.
 *
 * <p>Serve para lote de tamanho conhecido — que é o caso de uso do sistema, um
 * acervo de documentos de um período. Lote grande o bastante para que a lista de
 * falhas não caiba na memória é lote cujas falhas ninguém vai ler de qualquer
 * forma, e nesse cenário o destino certo é outra implementação de
 * {@link RegistroDeFalhasDeLeitura}.</p>
 *
 * <p>A lista é sincronizada porque o fluxo do {@link LeitorLote} pode ser
 * consumido em paralelo por quem o recebe.</p>
 */
public final class FalhasDeLeituraEmMemoria implements RegistroDeFalhasDeLeitura {

    private final List<FalhaDeLeitura> falhas = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void registrar(FalhaDeLeitura falha) {
        if (falha == null) {
            throw new IllegalArgumentException("Não se registra falha nula.");
        }
        falhas.add(falha);
    }

    /** Cópia das falhas registradas até agora, na ordem em que foram registradas. */
    public List<FalhaDeLeitura> falhas() {
        synchronized (falhas) {
            return List.copyOf(falhas);
        }
    }

    public boolean vazio() {
        return falhas.isEmpty();
    }

    public int quantidade() {
        return falhas.size();
    }
}
