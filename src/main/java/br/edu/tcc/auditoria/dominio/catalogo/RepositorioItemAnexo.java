package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;

import java.time.LocalDate;
import java.util.List;

// Interface responsável por buscar os vínculos entre NCM e anexo do catálogo numa data.
public interface RepositorioItemAnexo {

    // Retorna os vínculos do NCM que valiam na data; é lista porque o catálogo pode vincular o mesmo NCM a mais de um anexo.
    List<ItemAnexo> buscarVigentesEm(Ncm ncm, LocalDate data);
}
