# Entendendo e apresentando o projeto

## O que cada parte faz

| Parte | Responsabilidade |
|---|---|
| `Main.java` | Inicia o servidor Java. |
| `web/Servidor.java` | Recebe os pedidos do navegador e chama as classes do sistema. |
| `model` | Classes de produtos, clientes, carrinho, pedido, recibo, endereço e cartão cadastrado. |
| `interfaces` | Contratos de desconto e de pagamento. |
| `payment` | Implementações de Pix, crédito e débito e geração do QR Code com ZXing. |
| `service/VendaService.java` | Coordena verificação de estoque, pagamento, gravação e fechamento. |
| `storage/RepositorioBanco.java` | Executa SQL para gravar e recuperar produtos, clientes, vendas, itens e pagamentos. |
| `src/main/resources/public` | Página HTML, aparência CSS e interação JavaScript. |
| `src/test/java/.../Testes.java` | Verificações de funcionamento, incluindo a API. |

## Os conceitos de Java que vocês conseguem demonstrar

| Conceito | Onde mostrar | Como explicar com suas palavras |
|---|---|---|
| Classe | `Produto` | O modelo que define os dados e comportamentos de um produto. |
| Objeto | `new Produto(...)` em `Servidor` | Um produto específico, como a creatina, criado a partir da classe. |
| Atributos | `codigo`, `nome`, `preco`, `quantidadeEstoque` em `Produto` | Informações de cada produto. |
| Métodos | `debitarEstoque`, `adicionarItem`, `finalizarCompra` | Ações realizadas pelos objetos. |
| Construtor | `Produto(...)` | Recebe os dados iniciais e rejeita valores inválidos. |
| Encapsulamento | Atributos `private` de `Produto` | O estoque não pode ser alterado livremente; passa por métodos validados. |
| Herança | `ClientePadrao extends Cliente` e `ClienteVip extends Cliente` | Os dois tipos reutilizam nome, identificação, e-mail e cálculo básico do cliente. |
| Classe abstrata | `abstract class Cliente` | Uma base comum que não é instanciada diretamente. |
| Método abstrato | `percentualDesconto` em `Cliente` | Cada tipo de cliente precisa definir seu percentual. |
| Interface | `MetodoPagamento` | Um contrato: uma forma de pagamento precisa processar um valor e fornecer descrição. |
| Implementação | `PagamentoPix`, `PagamentoCartao`, `PagamentoDebito` | Classes diferentes que cumprem o mesmo contrato. |
| Polimorfismo | Parâmetro `MetodoPagamento` em `VendaService` | A venda chama o mesmo método; o comportamento muda conforme Pix, crédito ou débito. |
| Sobrescrita | Métodos com `@Override` | A classe fornece sua própria implementação do método da interface ou da classe mãe. |
| Sobrecarga | `finalizarCompra` em `VendaService` | Mesmo nome com parâmetros diferentes, incluindo uma versão que recebe endereço e ação de salvar. |
| Composição | `CarrinhoDeCompras` contém `ItemPedido` | O carrinho é formado por seus itens. |
| Associação | `ItemPedido` referencia `Produto` | Um item conhece o produto que representa. |
| Coleções e generics | `List<Produto>` e `List<ItemPedido>` | Listas com tipos definidos; armazenam vários objetos. |
| Enum | `EstadoPedido` | Conjunto fixo de situações possíveis de um pedido. |
| Exceções | `EstoqueInsuficienteException` e `PagamentoRecusadoException` | Interrompem uma operação inválida, com mensagem que a tela consegue mostrar. |
| `try/catch` | `VendaService` | Recupera estoque e desfaz a simulação do pagamento se o fechamento falhar. |
| Dinheiro com precisão | `BigDecimal` e `Moeda` | Evita usar `double` nas regras de dinheiro. |
| JDBC e SQL | `ConexaoBanco` e `RepositorioBanco` | Abre conexão, usa `PreparedStatement`, consulta `ResultSet` e grava dados relacionais. |
| Transações | `salvarVenda` em `RepositorioBanco` | Usa `commit` e `rollback` para evitar uma venda gravada pela metade. |
| `equals` e `hashCode` | `Produto` e `Cliente` | Define como reconhecer produtos pelo código e clientes pela identificação. |
| Imutabilidade | `ItemPedido` e `Recibo` | Quantidade/preço de um item não são alterados diretamente; recibo preserva os dados da venda. |

A herança principal é entre **clientes**, como no Java original. As formas de pagamento usam **interfaces**; não confundam implementação de interface com herança entre classes.

`ComprovantePagamento` usa `record`, recurso de Java para agrupar valores. `Consumer<Recibo>` representa a ação de salvar recebida pelo serviço. Esses recursos podem estar além da matéria já vista: estudem sua função no projeto e confirmem com o professor se há restrições. Não afirmem que um conteúdo foi dado sem conferir.

## Caminho de uma compra

1. A pessoa clica em Adicionar.
2. `app.js` envia um formulário HTTP para `/api/carrinho/adicionar`.
3. `Servidor` encontra o `Produto` e chama `CarrinhoDeCompras.adicionarItem`.
4. Ao finalizar, o Java consulta a assinatura pela identificação e pelo e-mail. Com mensalidade vigente, cria um `ClienteVip`. Nos demais casos, cria um `ClientePadrao`. Também valida o endereço e prepara o pagamento escolhido.
5. `VendaService` valida estoque e cria um `Pedido`.
6. O estoque é debitado e o pagamento fictício é processado.
7. Se houver aprovação, o Java monta o recibo e grava cliente, venda, endereço, itens, pagamento e estoque em uma única transação SQLite.
8. O pedido é concluído, o carrinho é limpo e o navegador mostra o recibo.
9. Se pagamento ou gravação falhar, o serviço restaura o estoque; quando necessário, estorna o saldo/limite da simulação. O carrinho é mantido.

O frontend não é o responsável por aprovar pagamento, calcular descontos ou decidir estoque. Ele mostra dados e envia ações. O Java valida novamente tudo que determina uma venda.

## Roteiro de demonstração (cerca de 5 a 7 minutos)

1. Buscar uma creatina no catálogo e abrir a foto para mostrar a ficha do produto retornada pelo Java.
2. Adicionar ao carrinho e abrir **VIP**. Cadastrar o cliente de demonstração e mostrar que a mensalidade está pendente. Gerar o Pix de R$ 19,90, confirmar na tela e mostrar o vencimento.
3. Abrir **Carrinho** e clicar em **Finalizar compra**. Mostrar os dados do cliente preenchidos com o cadastro ativo e o desconto de 10%, que baixa o preço da creatina de R$ 89,90 para R$ 80,91. Explicar que o Clube VIP dá 10% até R$ 700,00 e 15% acima desse subtotal.
4. Avançar para entrega e preencher um endereço fictício, como Rua Exemplo, 120, Centro, Fortaleza–CE, CEP `60000-000`.
5. Selecionar Pix, gerar o QR Code e clicar em **Confirmar pagamento**. Mostrar o endereço no recibo.
6. Voltar para a loja, adicionar whey e abrir o checkout pelo carrinho. Avançar até pagamento e escolher crédito em 3 parcelas.
7. Cadastrar o cartão fictício `4111 1111 1111 1111` com validade futura. Mostrar o cartão mascarado e concluir a compra.
8. Fazer outra compra por débito com o cartão já cadastrado. Mostrar que o débito é à vista.
9. Abrir Estoque, repor algumas unidades e mostrar a atualização.
10. Executar `consultar_banco.bat` e mostrar as tabelas e o relacionamento entre cliente, venda e pagamento.
11. Encerrar o Java com Ctrl+C, iniciar novamente e mostrar os recibos e o estoque recuperados do banco. Consultar a assinatura na aba **VIP** com os dados do cadastro.

Use nomes, endereços, e-mails e cartões fictícios na apresentação. A confirmação de pagamento é simulada.

## Divisão sugerida das falas

- Pessoa 1: objetivo da loja, tela, catálogo e carrinho.
- Pessoa 2: classes, objetos, encapsulamento e herança de clientes.
- Pessoa 3: interfaces, polimorfismo dos pagamentos, tratamento de erros e persistência.

Cada pessoa deve executar pelo menos uma compra e conseguir localizar o código que explica.

## Perguntas para ensaiar

- Qual é a diferença entre classe e objeto? Dê um exemplo do catálogo.
- Por que `Cliente` é abstrata?
- Qual é a vantagem de `VendaService` receber `MetodoPagamento`, em vez de testar cada pagamento internamente?
- Quais dados do cartão permanecem no SQLite depois do cadastro?
- O que impede comprar uma quantidade maior que o estoque?
- Por que usamos `BigDecimal`?
- Onde os dados ficam guardados? O que se perde ao encerrar?
- Por que não basta abrir o arquivo HTML com dois cliques?
- Qual é a diferença entre as regras de desconto do cliente e a forma de pagamento?

## Conferir o enunciado

Este projeto demonstra os conceitos listados acima. Ainda não recebemos o enunciado completo nem a lista de conteúdos da disciplina; portanto, não há garantia de cobrir tudo que o professor ensinou. Se ele exigir um banco diferente de SQLite, Swing, diagrama UML ou determinada biblioteca, isso precisa ser verificado separadamente.

Revisem, entendam e adaptem a entrega. Sigam as regras da disciplina sobre declaração de apoio de ferramentas.

## Explicando o banco sem decorar

- SQLite é o banco relacional; JDBC é a forma de o Java conversar com o banco.
- `Connection` representa a conexão; `PreparedStatement` envia SQL e parâmetros separados; `ResultSet` permite ler o resultado de um SELECT.
- A identificação única evita cadastrar o mesmo cliente a cada venda.
- Uma venda se relaciona com um cliente, vários itens e um pagamento.
- Os itens preservam o preço e nome do produto no momento da compra.
- `setAutoCommit(false)` inicia a unidade de gravação; `commit` confirma; `rollback` desfaz caso alguma etapa falhe.
- O driver é uma biblioteca fornecida pelo projeto Xerial, incluída em `lib`; não foi escrita pela equipe.
- Pagamentos recusados não geram venda concluída nem registro de pagamento no banco nesta versão.
