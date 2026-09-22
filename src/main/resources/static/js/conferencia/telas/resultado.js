/* ---------------------------------------------------------------------------
   O despachante do resultado.

   Uma nota abre a tela da nota; varias abrem a tela do lote. A pergunta e outra
   nos dois casos — "o que tem neste documento" contra "o que precisa ser
   corrigido no cadastro" — e por isso as telas sao duas, e nao uma com um
   parametro.

   Um pacote com um documento so continua sendo uma nota, e e assim que tem de
   aparecer: quem decide e a contagem que o servidor mandou, nao a extensao do
   arquivo que foi enviado.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import * as api from '../api.js';
import { avisoDeUso, blocoDaLeitura, cabecalhoDoResultado } from '../pecas.js';
import { enderecoDeEnvio } from '../roteador.js';

import * as telaDaNota from './nota.js';
import * as telaDoLote from './lote.js';

export async function desenhar(raiz, _parametros, rota) {
  const resposta = await api.analise(rota.analiseId);
  const conferencia = resposta.conferencia;

  /*
   * Execucao sem acervo: itens foram lidos, mas nenhum produto pode ser listado.
   * E o caso da rodada feita pela linha de comando, que nao grava
   * item_da_execucao. A tela diz isso em vez de mostrar "0 produtos" ao lado de
   * "300 itens lidos", que seria contradicao sem explicacao.
   */
  if (conferencia.quantidadeDeProdutos === 0 && resposta.leitura.itensLidos > 0) {
    trocar(raiz, [
      cabecalhoDoResultado(
        'Analise sem acervo de produtos',
        'Esta execucao leu itens, mas nao registrou quais. Nao ha como listar os produtos dela.',
        resposta.natureza,
      ),
      el('p', {
        texto: 'Execucoes feitas pelo comando "auditar", na linha de comando, gravam apontamentos '
          + 'mas nao gravam o acervo do que leram — ele so passou a existir com a analise por '
          + 'esta tela. Os apontamentos continuam disponiveis na visao tecnica.',
      }),
      blocoDaLeitura(resposta.leitura),
      el('div', { classe: 'acoes' }, [
        el('a', { classe: 'botao', href: 'tecnica.html#/execucao/' + encodeURIComponent(rota.analiseId),
          texto: 'Abrir na visao tecnica' }),
        el('a', { classe: 'botao', href: enderecoDeEnvio(), texto: 'Enviar outra nota' }),
      ]),
      avisoDeUso(resposta.aviso),
    ]);
    return;
  }

  if (conferencia.quantidadeDeNotas > 1) {
    await telaDoLote.desenhar(raiz, resposta, rota);
    return;
  }
  await telaDaNota.desenhar(raiz, resposta, rota);
}
