import { ehDecimal, somar, paraOrdenar } from './decimal.js';
import { regraEmTexto } from './formato.js';

const CAMPO_NCM = 'ncm';
const CAMPO_CLASSTRIB = 'cClassTrib';

const ORDEM_DE_SEVERIDADE = ['CRITICA', 'GRAVE', 'MODERADA', 'INFORMATIVA'];

export const NIVEIS = {
  NCM_E_CLASSTRIB: {
    codigo: 'NCM_E_CLASSTRIB',
    rotulo: 'agrupado por NCM + cClassTrib + regra',
    degradado: false,
  },
  SOMENTE_CLASSTRIB: {
    codigo: 'SOMENTE_CLASSTRIB',
    rotulo: 'agrupado por cClassTrib + regra (sem NCM)',
    degradado: true,
  },
  SOMENTE_NCM: {
    codigo: 'SOMENTE_NCM',
    rotulo: 'agrupado por NCM + regra (sem cClassTrib)',
    degradado: true,
  },
  SOMENTE_REGRA: {
    codigo: 'SOMENTE_REGRA',
    rotulo: 'agrupado somente por regra (sem NCM e sem cClassTrib)',
    degradado: true,
  },
};

export function valorNaEvidencia(achado, campo) {
  const evidencias = achado.evidencias || [];
  for (const evidencia of evidencias) {
    if (evidencia.campoAnalisado === campo
        && evidencia.valorEncontrado !== null
        && evidencia.valorEncontrado !== undefined) {
      return evidencia.valorEncontrado;
    }
  }
  return null;
}

function contarDisponibilidade(achados) {
  const porRegra = new Map();
  for (const achado of achados) {
    const contagem = porRegra.get(achado.regraId) || { total: 0, comNcm: 0, comClassTrib: 0 };
    contagem.total += 1;
    if (valorNaEvidencia(achado, CAMPO_NCM) !== null) {
      contagem.comNcm += 1;
    }
    if (valorNaEvidencia(achado, CAMPO_CLASSTRIB) !== null) {
      contagem.comClassTrib += 1;
    }
    porRegra.set(achado.regraId, contagem);
  }
  return porRegra;
}

function motivoDaAusencia(achado, campo, contagem) {
  const regra = regraEmTexto(achado);
  const quantos = campo === CAMPO_NCM ? contagem.comNcm : contagem.comClassTrib;
  if (quantos === 0) {
    return 'nenhuma das ' + contagem.total + ' ocorrencia(s) de ' + regra
      + ' nesta execucao traz o campo "' + campo + '" na evidencia; a regra nao examinou esse '
      + 'campo, entao o agrupamento nao o usa';
  }
  return 'esta ocorrencia de ' + regra + ' nao trouxe "' + campo + '" na evidencia, embora '
    + quantos + ' de ' + contagem.total + ' ocorrencia(s) da mesma regra tragam';
}

function nivelDe(ncm, cClassTrib) {
  if (ncm !== null && cClassTrib !== null) {
    return NIVEIS.NCM_E_CLASSTRIB;
  }
  if (cClassTrib !== null) {
    return NIVEIS.SOMENTE_CLASSTRIB;
  }
  if (ncm !== null) {
    return NIVEIS.SOMENTE_NCM;
  }
  return NIVEIS.SOMENTE_REGRA;
}

function maisGrave(a, b) {
  return ORDEM_DE_SEVERIDADE.indexOf(a) <= ORDEM_DE_SEVERIDADE.indexOf(b) ? a : b;
}

export function agrupar(achados) {
  const disponibilidade = contarDisponibilidade(achados);
  const grupos = new Map();

  for (const achado of achados) {
    const contagem = disponibilidade.get(achado.regraId);
    const ncm = valorNaEvidencia(achado, CAMPO_NCM);
    const cClassTrib = valorNaEvidencia(achado, CAMPO_CLASSTRIB);
    const nivel = nivelDe(ncm, cClassTrib);
    const chave = JSON.stringify([achado.regraId, ncm, cClassTrib]);

    let grupo = grupos.get(chave);
    if (!grupo) {
      grupo = {
        chave,
        regraId: achado.regraId,
        regraVersao: achado.regraVersao,

        regraNome: achado.regraNome || null,
        motivoDoNomeDaRegraAusente: achado.motivoDoNomeDaRegraAusente || null,

        nivel: nivel.codigo,
        rotuloDoNivel: nivel.rotulo,
        nivelDegradado: nivel.degradado,

        ncm,
        motivoDoNcmAusente:
          ncm === null ? motivoDaAusencia(achado, CAMPO_NCM, contagem) : null,
        cClassTrib,
        motivoDoCClassTribAusente:
          cClassTrib === null ? motivoDaAusencia(achado, CAMPO_CLASSTRIB, contagem) : null,

        severidade: achado.severidade,
        ocorrencias: 0,
        ocorrenciasComValor: 0,
        ocorrenciasSemValor: 0,
        valorSomado: null,
        derivacaoDaSoma: null,
        motivoDoValorAusente: null,
        observacaoDaSomaParcial: null,
        porTratativa: new Map(),
        documentos: new Set(),
        achados: [],
      };
      grupos.set(chave, grupo);
    }

    grupo.ocorrencias += 1;
    grupo.severidade = maisGrave(grupo.severidade, achado.severidade);
    grupo.achados.push(achado);
    grupo.documentos.add(achado.documento.pseudonimo);
    grupo.porTratativa.set(
      achado.statusDeTratativa,
      (grupo.porTratativa.get(achado.statusDeTratativa) || 0) + 1,
    );

    if (ehDecimal(achado.valorEmRisco)) {
      grupo.ocorrenciasComValor += 1;
      grupo.valorSomado = grupo.valorSomado === null
        ? achado.valorEmRisco
        : somar(grupo.valorSomado, achado.valorEmRisco);
    } else {
      grupo.ocorrenciasSemValor += 1;
    }
  }

  for (const grupo of grupos.values()) {
    concluirASoma(grupo);
  }
  return ordenar(Array.from(grupos.values()));
}

function concluirASoma(grupo) {
  if (grupo.ocorrenciasComValor === 0) {
    grupo.motivoDoValorAusente =
      'nenhuma das ' + grupo.ocorrencias + ' ocorrencia(s) deste grupo tem valor em risco '
      + 'calculavel; a regra apontou sem quantia a opor, e zero seria uma afirmacao que ela '
      + 'nao fez';
    return;
  }
  grupo.derivacaoDaSoma =
    'soma de ' + grupo.ocorrenciasComValor + ' de ' + grupo.ocorrencias + ' ocorrencia(s)';
  if (grupo.ocorrenciasSemValor > 0) {
    grupo.observacaoDaSomaParcial =
      'soma PARCIAL: ' + grupo.ocorrenciasSemValor + ' de ' + grupo.ocorrencias
      + ' ocorrencia(s) nao tem valor em risco calculavel e ficaram de fora da conta';
  }
}

function ordenar(todos) {
  const comValor = todos.filter((grupo) => grupo.valorSomado !== null);
  const semValor = todos.filter((grupo) => grupo.valorSomado === null);

  comValor.sort((a, b) => {
    const diferenca = paraOrdenar(b.valorSomado) - paraOrdenar(a.valorSomado);
    if (diferenca !== 0) {
      return diferenca;
    }
    if (b.ocorrencias !== a.ocorrencias) {
      return b.ocorrencias - a.ocorrencias;
    }
    return a.regraId.localeCompare(b.regraId);
  });

  semValor.sort((a, b) => {
    const gravidade = ORDEM_DE_SEVERIDADE.indexOf(a.severidade)
      - ORDEM_DE_SEVERIDADE.indexOf(b.severidade);
    if (gravidade !== 0) {
      return gravidade;
    }
    if (b.ocorrencias !== a.ocorrencias) {
      return b.ocorrencias - a.ocorrencias;
    }
    return a.regraId.localeCompare(b.regraId);
  });

  return { comValor, semValor, total: todos.length };
}

export function ordenarAchados(achados) {
  const comValor = achados.filter((achado) => ehDecimal(achado.valorEmRisco));
  const semValor = achados.filter((achado) => !ehDecimal(achado.valorEmRisco));
  comValor.sort((a, b) => paraOrdenar(b.valorEmRisco) - paraOrdenar(a.valorEmRisco));
  semValor.sort((a, b) => ORDEM_DE_SEVERIDADE.indexOf(a.severidade)
    - ORDEM_DE_SEVERIDADE.indexOf(b.severidade));
  return { comValor, semValor, total: achados.length };
}

export function porGravidade(grupos) {
  return Array.from(grupos).sort((a, b) => {
    const gravidade = ORDEM_DE_SEVERIDADE.indexOf(a.severidade)
      - ORDEM_DE_SEVERIDADE.indexOf(b.severidade);
    return gravidade !== 0 ? gravidade : b.ocorrencias - a.ocorrencias;
  });
}

export function porOcorrencias(grupos) {
  return Array.from(grupos).sort((a, b) => b.ocorrencias - a.ocorrencias);
}
