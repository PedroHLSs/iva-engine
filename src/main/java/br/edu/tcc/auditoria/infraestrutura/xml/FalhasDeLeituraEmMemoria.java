package br.edu.tcc.auditoria.infraestrutura.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Classe que guarda as falhas de um lote na memória, na ordem em que aconteceram. A lista é sincronizada porque a leitura pode ser em paralelo.
public final class FalhasDeLeituraEmMemoria implements RegistroDeFalhasDeLeitura {

    private final List<FalhaDeLeitura> falhas = Collections.synchronizedList(new ArrayList<>());

    // Registra uma falha de leitura.
    @Override
    public void registrar(FalhaDeLeitura falha) {
        if (falha == null) {
            throw new IllegalArgumentException("Não se registra falha nula.");
        }
        falhas.add(falha);
    }

    // Devolve uma cópia das falhas registradas até agora, na ordem em que aconteceram.
    public List<FalhaDeLeitura> falhas() {
        synchronized (falhas) {
            return List.copyOf(falhas);
        }
    }

    // Diz se nenhuma falha foi registrada.
    public boolean vazio() {
        return falhas.isEmpty();
    }

    // Retorna quantas falhas foram registradas.
    public int quantidade() {
        return falhas.size();
    }
}
