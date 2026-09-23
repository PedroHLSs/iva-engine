package br.edu.tcc.auditoria.aplicacao.auditoria;

import java.nio.file.Path;

// Feito para obter os documentos de uma origem
public interface FonteDeLoteDeDocumentos {
    
    LoteDeDocumentos abrir(Path origem);
}
