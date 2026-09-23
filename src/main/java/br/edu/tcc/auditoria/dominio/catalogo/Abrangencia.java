package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

// Representa o recorte a que uma alíquota do catálogo se aplica; é um rótulo opaco, e o domínio não conhece quais abrangências existem.
public record Abrangencia(String valor) {

    // Valida que a abrangência não seja nula, vazia nem tenha espaço em volta.
    public Abrangencia {
        if (valor == null) {
            throw new RegistroNormativoInvalido("A abrangência da alíquota não pode ser nula.");
        }
        if (valor.isBlank()) {
            throw new RegistroNormativoInvalido("A abrangência da alíquota não pode ser vazia.");
        }
        if (!valor.equals(valor.strip())) {
            throw new RegistroNormativoInvalido(
                    "A abrangência não pode ter espaço em volta: \"%s\". Normalizar o texto lido do CSV "
                            + "é tarefa da importação, não do domínio.".formatted(valor));
        }
    }
}
