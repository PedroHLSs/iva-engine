/* ---------------------------------------------------------------------------
   A base tributaria carregada, numa data.

   A data e obrigatoria, e a tela abre PEDINDO a data em vez de escolher uma.
   A D003 proibiu que uma regra escolhesse a data da consulta e previu que "o
   que vale hoje" seria um caso de uso proprio, com data explicita. Um padrao
   silencioso de "hoje" traria o problema de volta pela porta da frente: a
   resposta mudaria sozinha de um dia para o outro, e uma impressao da tela nao
   diria a que dia se refere.

   O botao "hoje" existe, e nao contradiz isso: ele e a pessoa escolhendo hoje,
   com um clique que ela deu.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import { data as formatarData } from '../../formato.js';
import * as api from '../api.js';
import { avisoDeUso, faixaDeNatureza, leituraDoCatalogo, valorOuMotivo } from '../pecas.js';
import { enderecoDaBase, irPara } from '../roteador.js';

export async function desenhar(raiz, parametros, rota) {
  const dataPedida = rota.parametros.get('data') || '';
  const ncmPedido = rota.parametros.get('ncm') || '';
  const classTribPedido = rota.parametros.get('cClassTrib') || '';

  const corpo = el('div', {});

  trocar(raiz, [
    el('h1', { texto: 'Base tributaria carregada' }),
    el('p', {
      classe: 'sub',
      texto: 'Consulta somente leitura do catalogo importado. Toda resposta e datada, porque o '
        + 'mesmo catalogo responde coisas diferentes em datas diferentes — e por isso a data e '
        + 'obrigatoria.',
    }),
    formulario(dataPedida, ncmPedido, classTribPedido),
    corpo,
  ]);

  if (!dataPedida) {
    trocar(corpo, [
      el('p', {
        classe: 'aviso',
        texto: 'Escolha uma data para consultar. Sem data a resposta nao diz a que dia se refere.',
      }),
    ]);
    return;
  }

  trocar(corpo, [el('p', { classe: 'carregando', texto: 'Consultando...' })]);
  const resposta = await api.baseTributaria(dataPedida, ncmPedido, classTribPedido);
  trocar(corpo, [conteudo(resposta)]);
}

function formulario(dataPedida, ncmPedido, classTribPedido) {
  const campoData = el('input', { type: 'date', id: 'data', value: dataPedida || null });
  const campoNcm = el('input', {
    type: 'text', id: 'ncm', value: ncmPedido || null, inputmode: 'numeric', maxlength: '8',
  });
  const campoClassTrib = el('input', {
    type: 'text', id: 'cClassTrib', value: classTribPedido || null,
  });

  const consultar = () => irPara(enderecoDaBase({
    data: campoData.value,
    ncm: campoNcm.value,
    cClassTrib: campoClassTrib.value,
  }));

  /*
   * Um <div>, e nao um <form>: formulario sem tratador de envio recarrega a
   * pagina quando alguem aperta Enter num campo de texto, e a consulta some.
   */
  return el('div', { classe: 'filtros' }, [
    el('div', { classe: 'filtro' }, [
      el('label', { for: 'data', texto: 'data (obrigatoria)' }),
      campoData,
    ]),
    el('div', { classe: 'filtro' }, [
      el('label', { for: 'ncm', texto: 'NCM (opcional)' }),
      campoNcm,
    ]),
    el('div', { classe: 'filtro' }, [
      el('label', { for: 'cClassTrib', texto: 'cClassTrib (opcional)' }),
      campoClassTrib,
    ]),
    el('div', { classe: 'acoes' }, [
      el('button', { type: 'button', classe: 'botao principal', texto: 'consultar',
        aoClicar: consultar }),
      el('button', {
        type: 'button',
        classe: 'botao',
        texto: 'hoje',
        title: 'preenche a data com a de hoje — a escolha continua sendo sua, e fica escrita',
        aoClicar: () => {
          const agora = new Date();
          const mes = String(agora.getMonth() + 1).padStart(2, '0');
          const dia = String(agora.getDate()).padStart(2, '0');
          campoData.value = `${agora.getFullYear()}-${mes}-${dia}`;
        },
      }),
    ]),
  ]);
}

function conteudo(resposta) {
  return el('div', {}, [
    faixaDeNatureza(resposta.natureza),

    el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Carga consultada' }),
      el('dl', { classe: 'identificacao' }, [
        el('dt', { texto: 'data da consulta' }),
        el('dd', { classe: 'mono', texto: formatarData(resposta.data) }),
        el('dt', { texto: 'versao do catalogo' }),
        el('dd', { classe: 'mono', texto: resposta.versaoDoCatalogo }),
        el('dt', { texto: 'carga gravada' }),
        el('dd', { texto: resposta.cargaDisponivel ? 'sim' : 'nao' }),
      ]),
    ]),

    /*
     * A cobertura abre a tela: e ela que separa "a carga nao traz este registro"
     * de "esta tabela nao foi carregada para esta data" (D004). Quem for ler a
     * base precisa dela antes de ler qualquer linha, ou lera silencio como
     * ausencia.
     */
    el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Cobertura declarada da carga' }),
      leituraDoCatalogo(resposta.cobertura, (linhas) =>
        el('ul', { classe: 'lista-catalogo' }, linhas.map((linha) => el('li', {}, [
          el('strong', { texto: linha.tabela }),
          referenciaCurta(linha.referencia),
        ])))),
    ]),

    el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Aliquotas vigentes na data' }),
      el('div', { classe: 'tributos' }, resposta.aliquotas.map((doTributo) =>
        el('div', { classe: 'tributo' }, [
          el('p', { classe: 'tributo-rotulo', texto: doTributo.rotulo }),
          leituraDoCatalogo(doTributo.aliquotas, (aliquotas) =>
            el('ul', { classe: 'lista-catalogo' }, aliquotas.map((aliquota) => el('li', {}, [
              el('span', { classe: 'mono percentual', texto: aliquota.percentual }),
              ' — ',
              el('span', { texto: aliquota.abrangencia }),
              referenciaCurta(aliquota.referencia),
            ])))),
        ]))),
    ]),

    blocoConsultado('NCM consultado', resposta.ncmPerguntado, resposta.ncmConsultado,
      (registros) => el('ul', { classe: 'lista-catalogo' }, registros.map((registro) => el('li', {}, [
        el('span', { classe: 'mono', texto: registro.ncm }),
        ' — ',
        el('span', { texto: registro.descricao }),
        referenciaCurta(registro.referencia),
      ])))),

    blocoConsultado('Anexos do NCM consultado', resposta.ncmPerguntado, resposta.anexosDoNcm,
      (registros) => el('ul', { classe: 'lista-catalogo' }, registros.map((registro) => el('li', {}, [
        el('strong', { texto: registro.anexo }),
        ' — ',
        el('span', { texto: registro.tipoDeTratamento }),
        referenciaCurta(registro.referencia),
      ])))),

    blocoConsultado('cClassTrib consultado', resposta.classTribPerguntado,
      resposta.classificacaoConsultada,
      (registros) => el('ul', { classe: 'lista-catalogo' }, registros.map((registro) => el('li', {}, [
        el('span', { classe: 'mono', texto: registro.codigo }),
        ' — ',
        el('span', { texto: registro.dispositivoLegal }),
        el('p', { classe: 'nota' }, [
          'CST admitidos: ',
          registro.cstsAdmitidos.length
            ? el('span', { classe: 'mono', texto: registro.cstsAdmitidos.join(', ') })
            : valorOuMotivo(null, registro.motivoSemCstAdmitido),
        ]),
        el('p', { classe: 'nota' }, [
          'reducao: ',
          valorOuMotivo(registro.percentualReducao, registro.motivoSemReducao),
        ]),
        referenciaCurta(registro.referencia),
      ])))),

    el('p', { classe: 'nota', texto: resposta.comoConsultar }),
    avisoDeUso(resposta.aviso),
  ]);
}

/**
 * Um bloco de consulta pontual.
 *
 * Nao perguntado e perguntado-sem-resposta sao estados diferentes: o primeiro
 * some da tela, o segundo aparece com o motivo. Colapsar os dois faria a tela
 * parecer ter consultado o que nao consultou.
 */
function blocoConsultado(titulo, perguntado, leitura, comoDesenhar) {
  if (perguntado === null || perguntado === undefined) {
    return null;
  }
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: titulo }),
    el('p', { classe: 'nota' }, ['perguntado: ', el('span', { classe: 'mono', texto: perguntado })]),
    leituraDoCatalogo(leitura, comoDesenhar),
  ]);
}

function referenciaCurta(ref) {
  if (!ref) {
    return el('span', { classe: 'ausente', texto: 'sem referencia na resposta' });
  }
  return el('p', { classe: 'referencia' }, [
    el('span', { texto: 'de ' + formatarData(ref.vigenciaInicio) + ' ate ' }),
    ref.vigenciaFim
      ? el('span', { texto: formatarData(ref.vigenciaFim) })
      : el('span', { classe: 'ausente', texto: ref.motivoDaVigenciaSemFim }),
    el('span', { texto: ' — fonte: ' + ref.fonteNormativa }),
  ]);
}
