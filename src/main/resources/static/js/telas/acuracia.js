/* ---------------------------------------------------------------------------
   Tela 5 - Acuracia.

   ESTA TELA NAO FALA COM A API, E ISSO E DECISAO, NAO FALTA

   A Etapa 8 removeu /api/execucoes/{id}/acuracia de proposito: acuracia e
   propriedade de uma MEDICAO contra gabarito, nao de uma execucao de auditoria,
   e o harness nao persiste nada - medir nao e auditar. Entao a fonte aqui e o
   proprio CSV que o comando "avaliar-acuracia" grava, lido dentro do navegador.
   Nenhum byte sai da maquina e a tela funciona sem rede.

   O QUE ELA SE RECUSA A LER

   O cabecalho de comentarios do CSV lista, em texto claro, as chaves de acesso
   dos enderecos do gabarito que o motor nao avaliou. Os digitos intermediarios
   da chave sao o CNPJ do emitente. A tela CONTA essas linhas e nunca as
   escreve, do mesmo modo que visualizacao/resultados.html.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../dom.js';
import { inteiro, metrica, INDEFINIDA } from '../formato.js';
import { barrasDeCobertura } from '../svg.js';

const SEPARADOR = ';';

/** Compara texto ignorando acento e caixa, para nao depender da grafia do CSV. */
function chave(texto) {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

const ROTULOS = [
  { campo: 'catalogo', prefixo: 'catalogo:' },
  { campo: 'conjuntoDeRegras', prefixo: 'conjunto de regras:' },
  { campo: 'documentos', prefixo: 'documentos auditados:' },
  { campo: 'itens', prefixo: 'itens auditados:' },
  { campo: 'avaliacoesProduzidas', prefixo: 'avaliacoes produzidas pelo motor:' },
  { campo: 'gabaritoSemAvaliacao', prefixo: 'linhas de gabarito sem avaliacao correspondente:' },
  { campo: 'avaliacoesSemGabarito', prefixo: 'avaliacoes sem linha no gabarito:' },
  { campo: 'linhasDoGabarito', prefixo: 'linhas de gabarito:' },
];

const MARCA_DOS_ENDERECOS = 'enderecos do gabarito que o motor nao avaliou:';

/**
 * Le o relatorio.
 *
 * As linhas iniciadas por "#" sao cabecalho de comentario. Depois da marca dos
 * enderecos, cada comentario carrega uma chave de acesso: essas sao contadas e
 * descartadas na leitura, e nao chegam a existir como dado nesta pagina.
 */
export function lerRelatorio(texto) {
  const identificacao = { enderecosOmitidos: 0 };
  const linhas = [];
  let cabecalho = null;
  let dentroDosEnderecos = false;

  for (const bruta of texto.split(/\r?\n/)) {
    const linha = bruta.trim();
    if (linha === '') {
      continue;
    }
    if (linha.startsWith('#')) {
      const conteudo = chave(linha.replace(/^#\s*/, ''));
      if (conteudo.startsWith(MARCA_DOS_ENDERECOS)) {
        dentroDosEnderecos = true;
        continue;
      }
      if (dentroDosEnderecos) {
        identificacao.enderecosOmitidos += 1;
        continue;
      }
      for (const rotulo of ROTULOS) {
        if (conteudo.startsWith(rotulo.prefixo) && identificacao[rotulo.campo] === undefined) {
          identificacao[rotulo.campo] = linha.replace(/^#\s*/, '').slice(rotulo.prefixo.length)
            .trim();
          break;
        }
      }
      continue;
    }
    const celulas = linha.split(SEPARADOR).map((celula) => celula.trim());
    if (cabecalho === null) {
      cabecalho = celulas.map(chave);
      continue;
    }
    const registro = {};
    cabecalho.forEach((nome, indice) => {
      registro[nome] = celulas[indice] === undefined ? '' : celulas[indice];
    });
    linhas.push(registro);
  }

  if (cabecalho === null) {
    throw new Error('O arquivo nao tem linha de cabecalho: nao parece o CSV do avaliar-acuracia.');
  }
  return { identificacao, linhas };
}

export async function desenhar(tela) {
  const conteudo = el('div', {});
  const estado = el('p', { classe: 'nota', texto: 'Nenhum arquivo escolhido.' });

  const entrada = el('input', {
    type: 'file', accept: '.csv,text/csv',
    aoMudar: async (evento) => {
      const arquivo = evento.target.files && evento.target.files[0];
      if (!arquivo) {
        return;
      }
      estado.textContent = 'Lendo ' + arquivo.name + '...';
      try {
        const relatorio = lerRelatorio(await arquivo.text());
        estado.textContent = arquivo.name + ' - ' + relatorio.linhas.length + ' linha(s).';
        trocar(conteudo, desenharRelatorio(relatorio));
      } catch (erro) {
        estado.textContent = '';
        trocar(conteudo, [el('div', { classe: 'aviso erro' }, [
          el('strong', { texto: 'Nao foi possivel ler o arquivo. ' }),
          erro.message || String(erro),
        ])]);
      }
    },
  });

  trocar(tela, [
    el('h1', { texto: 'Acuracia' }),
    el('p', {
      classe: 'sub',
      texto: 'Esta tela nao consulta a API. Acuracia e propriedade de uma medicao contra '
        + 'gabarito, nao de uma execucao de auditoria, e o harness nao persiste nada: medir nao '
        + 'e auditar. Escolha o CSV que o comando "avaliar-acuracia" gravou - o arquivo e lido '
        + 'aqui dentro do navegador e nenhum byte sai da maquina.',
    }),
    el('div', { classe: 'filtros' }, [
      el('div', { classe: 'filtro' }, [
        el('label', { texto: 'relatorio de acuracia (.csv)' }),
        entrada,
        estado,
      ]),
    ]),
    conteudo,
  ]);
}

function desenharRelatorio(relatorio) {
  const id = relatorio.identificacao;
  const consolidado = relatorio.linhas.find((linha) => linha.regra_id === 'CONSOLIDADO');
  const porRegra = relatorio.linhas.filter((linha) => linha.regra_id !== 'CONSOLIDADO');

  const par = (rotulo, valor) => [
    el('dt', { texto: rotulo }),
    el('dd', { texto: valor === undefined ? 'nao declarado no arquivo' : valor }),
  ];

  return [
    el('section', { classe: 'cartao' }, [
      el('dl', { classe: 'identificacao' }, [].concat(
        par('catalogo', id.catalogo),
        par('conjunto de regras', id.conjuntoDeRegras),
        par('documentos auditados', id.documentos),
        par('itens auditados', id.itens),
        par('avaliacoes produzidas', id.avaliacoesProduzidas),
        par('linhas de gabarito', id.linhasDoGabarito),
        par('avaliacoes sem linha no gabarito', id.avaliacoesSemGabarito),
        par('gabarito sem avaliacao', id.gabaritoSemAvaliacao),
      )),
    ]),
    id.enderecosOmitidos > 0
      ? el('div', { classe: 'aviso' }, [
        el('strong', { texto: 'Enderecos omitidos de proposito. ' }),
        'O cabecalho do arquivo lista ' + inteiro(id.enderecosOmitidos) + ' endereco(s) do '
          + 'gabarito que o motor nao avaliou, com a chave de acesso em texto claro. Os digitos '
          + 'intermediarios da chave carregam o CNPJ do emitente, entao esta pagina conta essas '
          + 'linhas e nao as escreve. Elas estao no arquivo, para quem precisar corrigir o '
          + 'gabarito.',
      ])
      : null,
    el('h3', { texto: 'por regra' }),
    barrasDeCobertura(porRegra),
    el('div', { classe: 'legenda' }, [
      el('span', {}, [el('i', { style: 'background:var(--conforme)' }), 'avaliados (entram na metrica)']),
      el('span', {}, [el('i', { style: 'background:var(--naoavaliado)' }), 'nao avaliados (nao entram)']),
      el('span', {}, [el('i', { style: 'background:var(--tinta3)' }), 'sem avaliacao (nao entram)']),
    ]),
    tabela(porRegra, consolidado),
    el('p', {
      classe: 'nota',
      texto: 'nao_avaliados e sem_avaliacao ficam fora de precisao, recall e f1. Aparecem em '
        + 'cobertura, que e avaliados / total. O consolidado soma celulas; nao e a media das '
        + 'metricas por regra.',
    }),
  ];
}

/**
 * Por que uma metrica ficou sem denominador, lido da propria linha.
 *
 * O CSV escreve "(indefinida)" e nao diz o motivo - a coluna nao teria onde
 * couber. Aqui o motivo e recomposto das contagens da mesma linha, que estao
 * todas no arquivo, e vai escrito embaixo do valor. Metrica indefinida sem
 * explicacao convida a leitura de "deu zero".
 */
function motivoDaIndefinicao(linha, nome) {
  const n = (coluna) => Number(linha[coluna] || 0);
  if (nome === 'precisao') {
    return 'sem denominador: VP + FP = 0, ou seja, a regra nao apontou nada neste recorte';
  }
  if (nome === 'recall') {
    return 'sem denominador: VP + FN = 0, ou seja, o gabarito nao marcou ACHADO para esta regra';
  }
  if (nome === 'f1') {
    return 'sem denominador: depende de precisao e recall, e ao menos uma das duas e indefinida';
  }
  return 'sem denominador: total = ' + n('total') + ', nenhuma linha para medir';
}

function celulaDeMetrica(linha, nome) {
  const valor = linha[nome];
  if (valor === INDEFINIDA || valor === '' || valor === undefined) {
    return el('td', {}, [
      metrica(INDEFINIDA),
      el('p', { classe: 'nota', texto: motivoDaIndefinicao(linha, nome) }),
    ]);
  }
  return el('td', { classe: 'num' }, [metrica(valor)]);
}

function linhaDaTabela(linha, consolidada) {
  const numero = (coluna) => el('td', { classe: 'num', texto: inteiro(Number(linha[coluna] || 0)) });
  const celulas = [
    el('td', { classe: 'mono', texto: linha.regra_id }),
    numero('verdadeiros_positivos'),
    numero('falsos_positivos'),
    numero('falsos_negativos'),
    numero('verdadeiros_negativos'),
    numero('avaliados'),
    numero('nao_avaliados'),
    numero('sem_avaliacao'),
    numero('total'),
    celulaDeMetrica(linha, 'precisao'),
    celulaDeMetrica(linha, 'recall'),
    celulaDeMetrica(linha, 'f1'),
    celulaDeMetrica(linha, 'cobertura'),
  ];
  const tr = el('tr', {}, celulas);
  if (consolidada) {
    tr.style.fontWeight = '640';
    tr.style.background = 'var(--superficie2)';
  }
  return tr;
}

function tabela(porRegra, consolidado) {
  const cabecalhos = [
    'regra', 'VP', 'FP', 'FN', 'VN', 'avaliados', 'nao avaliados', 'sem avaliacao', 'total',
    'precisao', 'recall', 'F1', 'cobertura',
  ];

  return el('div', { classe: 'rolagem' }, [
    el('table', {}, [
      el('caption', {
        texto: 'Cobertura fica ao lado das tres metricas, na mesma linha: uma precisao alta '
          + 'medida sobre um decimo do acervo nao diz o mesmo que a mesma precisao medida sobre '
          + 'tudo.',
      }),
      el('thead', {}, [
        el('tr', {}, cabecalhos.map((texto, indice) => el('th', {
          classe: indice > 0 && indice < 9 ? 'num' : '', texto,
        }))),
      ]),
      el('tbody', {}, porRegra.map((linha) => linhaDaTabela(linha, false))),
      consolidado
        ? el('tfoot', {}, [linhaDaTabela(consolidado, true)])
        : null,
    ]),
  ]);
}
