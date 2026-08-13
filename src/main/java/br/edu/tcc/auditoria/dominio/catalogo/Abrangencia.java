package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

/**
 * Recorte a que uma alíquota do catálogo se aplica.
 *
 * <p>Rótulo opaco, validado só quanto à forma. O domínio não conhece quais
 * abrangências existem, como se relacionam entre si nem qual prevalece sobre
 * qual — isso é conteúdo normativo e chega por importação. Aqui a abrangência
 * serve apenas como parte da chave de consulta.</p>
 */
public record Abrangencia(String valor) {

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
