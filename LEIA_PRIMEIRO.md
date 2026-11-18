# IronStore — loja de suplementos

Versão integrada para o trabalho de Programação Orientada a Objetos.

- Backend: **Java**. As vendas, preços, descontos, pagamentos, estoque e recibos são processados no Java.
- Frontend: HTML, CSS e JavaScript, exibidos no navegador.
- Banco de dados relacional: **SQLite**, acessado pelo Java com **JDBC**. O arquivo é `dados/loja.db`, com tabelas de produtos, clientes, vendas, itens e pagamentos.
- Pagamentos simulados por Pix com QR Code, cartão de crédito e cartão de débito.

## 1. Abrir e usar no Windows

1. Extraia o ZIP inteiro. Não abra o programa de dentro do ZIP.
2. Abra a pasta `LojaSuplementos` no VS Code (a pasta deste arquivo).
3. Abra **Terminal > Novo Terminal**.
4. Execute `java -version`. É necessário Java **17 ou superior**. O Java 25 do projeto anterior também serve.
5. Execute:

```powershell
java -jar loja-suplementos.jar
```

6. Espere a mensagem `Loja iniciada`.
7. Abra **http://localhost:8080** no navegador.
8. Mantenha o terminal aberto. Para encerrar, pressione `Ctrl+C` nele.

Também é possível dar dois cliques em `iniciar.bat` na pasta extraída. O JAR já está compilado. Mantenha a pasta `lib` junto ao JAR para carregar o driver SQLite e a biblioteca ZXing que gera o QR Code. Abra a página pelo endereço do servidor Java.

Se a porta estiver ocupada, encerre outra cópia da loja ou execute:

```powershell
java -Dporta=8081 -jar loja-suplementos.jar
```

Nesse caso, abra http://localhost:8081.

## 2. O que a tela faz

- **Loja.** Busque pelo nome, filtre pela categoria e clique na foto para ver a embalagem, a descrição e os sabores.
- **Carrinho.** Revise os produtos, altere quantidades e clique em **Finalizar compra** para abrir a página de checkout.
- **Checkout.** Preencha os dados do cliente, informe o endereço de entrega e passe à escolha de pagamento.
- **Clube VIP.** Cadastre-se, pague a mensalidade por Pix simulado e consulte quando o benefício vence.
- **Cartões:** cadastro de cartão fictício e seleção pelo mesmo identificador do cliente em compras seguintes.
- **Pix:** QR Code e código para copiar. A compra é concluída após confirmar o pagamento simulado na tela.
- **Estoque:** consulta e reposição. Venda concluída reduz estoque; recusa não reduz.
- **Recibos:** histórico textual com os valores, produtos, forma de pagamento e endereço de entrega.

### Clube VIP e descontos

| Cliente | Desconto |
|---|---|
| Padrão | 0% abaixo de R$ 500 e 5% a partir de R$ 500. |
| VIP com mensalidade ativa | 10% até R$ 700,00 e 15% acima de R$ 700,00. |

1. Abra **VIP**, informe um nome e dados fictícios como `DEMO-01` e `cliente@example.com`, e clique em **Cadastrar ou consultar**.
2. Clique em **Pagar mensalidade por Pix**. O QR Code é de R$ 19,90. Use **Confirmar pagamento** para ativar o plano e ver o vencimento.
3. Abra o checkout pelo carrinho na mesma aba. Os dados do cadastro ativo vêm preenchidos. Em outra aba, use a mesma identificação e o mesmo e-mail do clube. O Java reconhece a mensalidade vigente e aplica o desconto.

Cada mensalidade dá direito a um mês de benefício. Renove pelo próprio Clube VIP. Se renovar antes do vencimento, a validade atual é prorrogada por mais um mês. Após o vencimento, o Java aplica a regra de cliente padrão até a próxima confirmação.

### Pagamentos

| Tipo | Simulação |
|---|---|
| Pix | Gera QR Code e Pix Copia e Cola com o valor do pedido e uma chave de demonstração. |
| Crédito | Seleção de cartão cadastrado e 1 a 12 parcelas sem juros. |
| Débito | Seleção de cartão cadastrado e pagamento à vista. |

Para experimentar o cartão, preencha os dados do cliente, escolha crédito ou débito e clique em **Cadastrar cartão**. Use o número fictício `4111 1111 1111 1111`, um nome fictício e um mês de validade futuro. O SQLite guarda o titular, o mês de validade e os quatro últimos dígitos. Use a mesma identificação do cliente para encontrar o cartão em outra compra.

Para experimentar o Pix, escolha **Gerar QR Code Pix** e depois **Confirmar pagamento**. O código usa uma chave reservada para a demonstração.

Os valores monetários são calculados no Java com `BigDecimal`. A prévia de parcelas na tela é aproximada, por conta do arredondamento de centavos; o total da venda é o valor de referência da simulação. Não há cobrança nem distribuição bancária de parcelas.

A identificação é somente um texto de demonstração (`DEMO-001`, por exemplo). Não há validação de dígitos de CPF; o campo não exige um CPF real.

## 3. Alterar código e recompilar

O JAR não se atualiza sozinho ao editar arquivos. Após alterar Java, HTML, CSS ou JavaScript:

1. Encerre a loja com `Ctrl+C`.
2. Instale/use um **JDK** 17 ou superior (inclui `javac` e `jar`).
3. Execute no terminal do VS Code:

```powershell
.\compilar.bat
java -jar loja-suplementos.jar
```

4. Recarregue o navegador com `Ctrl+F5`.

Os scripts usam os drivers já presentes na pasta `lib` e não baixam dependências. `pom.xml` permite reconhecer a pasta como projeto Maven no editor; a execução sugerida não precisa de Maven.

No Linux/macOS:

```sh
sh compilar.sh
sh iniciar.sh
```

## 4. Executar testes

No Windows: `testar.bat`. No Linux/macOS: `sh testar.sh`.

Os testes são escritos em Java e usam uma pasta temporária, sem mexer nos dados de apresentação. Não dependem de JUnit. Os testes de persistência usam o driver SQLite incluído em `lib`.

## 5. Verificar a execução em outro computador

Antes de apresentar, testem no notebook e no navegador que serão usados. Verifiquem especialmente tamanho de tela, abertura do Java e permissões da pasta.

## 6. Dados e limitações

- Produtos, clientes, cartões mascarados, vendas, endereços, itens e pagamentos persistem em `dados/loja.db`. A venda é confirmada depois do `commit` da transação SQL.
- O carrinho e o QR Code Pix pendente são temporários e são descartados ao encerrar o Java.
- Uma única demonstração local e um único carrinho. Abas abertas compartilham esse carrinho.
- O endereço é registrado localmente no pedido. A confirmação de pagamento é simulada.
- O servidor atende somente no computador local.
- Para reiniciar a demonstração do zero, pare o Java e **renomeie** `dados/loja.db` para `loja-backup.db`. Reinicie o Java; o banco é recriado com o catálogo padrão e histórico vazio. Guarde o backup se precisar recuperar as vendas.
- Se o banco estiver inválido, a aplicação pode recusar a inicialização. Não sobrescreva o arquivo: restaure um backup ou guarde-o com outro nome antes de recomeçar.

## 7. O que mudou em relação aos ZIPs recebidos

O Java original foi reaproveitado: produtos, clientes padrão/VIP, carrinho, pedido, estados, pagamento e recibo. Foram acrescentados débito, servidor HTTP, persistência relacional SQLite/JDBC e testes. A configuração saiu de Java 25 obrigatório para código compatível com Java 17 ou superior.

A identidade visual escura e laranja do frontend do colega foi reaproveitada. A tela e o JavaScript foram adaptados ao backend Java. O servidor Node.js e suas classes de negócio **não fazem parte desta entrega**.

O catálogo inicial é definido no Java e a tela permite repor estoque. O Java calcula os descontos e valida o parcelamento. O checkout coleta o endereço de entrega, permite cadastrar um cartão fictício e gera o QR Code do Pix.

## Verificação desta entrega

- A suíte com `testar.bat` cobre domínio, API HTTP e JDBC/SQLite no Java 17.
- As verificações incluem leitura da imagem do QR Code, Pix cancelado ou confirmado, cartão mascarado, validade e renovação do Clube VIP, persistência do endereço, estoque, descontos e rollback de uma venda.

A lista exata de conteúdos da disciplina ainda deve ser comparada com o material do professor. O guia identifica o que o código efetivamente demonstra, sem presumir todo o conteúdo da turma.

## 8. Mostrar o banco na apresentação

O banco padrão está em `dados/loja.db`. Ao abrir a loja, o Java inclui os produtos que faltam no catálogo de nove itens e preserva os preços, estoques e vendas registrados. Se o arquivo ainda não existir, o programa cria o banco automaticamente.

Depois de concluir uma compra, execute `consultar_banco.bat` no Windows ou `sh consultar_banco.sh` no Linux/macOS. O terminal mostra a quantidade de produtos, clientes, vendas, itens e pagamentos, além de uma consulta relacionando cliente, venda e pagamento.

As instruções SQL estão em `banco/schema.sql`. O Java usa a mesma estrutura em `src/main/resources/banco/schema.sql`. Os exemplos em `banco/consultas.sql` incluem endereços e cartões mascarados. Ao abrir um banco de uma versão anterior, a loja cria as tabelas do Clube VIP no próprio arquivo.

| Tabela | Dados |
|---|---|
| `produtos` | Código, nome, preço em centavos e estoque. |
| `clientes` | Identificação única, nome, e-mail e tipo. |
| `vendas` | Cliente, data, valores, estado e recibo textual. |
| `itens_venda` | Produtos de cada venda, quantidades e preço histórico. |
| `pagamentos` | Forma, parcelas, valor e autorização fictícia. |
| `enderecos_entrega` | Endereço associado ao pedido. |
| `cartoes` | Cartões do cliente com titular, validade e quatro últimos dígitos. |
| `assinaturas_vip` | Identificação, nome e e-mail registrados no Clube VIP. |
| `mensalidades_vip` | Pagamentos simulados do clube, com data, valor, autorização e vencimento do benefício. |

Uma venda usa transação: baixa de estoque e INSERTs das quatro tabelas de negócio são confirmados juntos com `commit`. Em erro, `rollback` desfaz as mudanças no banco; o serviço também restaura o estoque em memória e a simulação do saldo/limite.

Os valores são armazenados como **centavos inteiros** no SQLite: `8990` representa R$ 89,90. No Java, os cálculos continuam com `BigDecimal`. As consultas de apresentação dividem por `100.0` somente para facilitar a leitura em reais.

A versão anterior usava `.properties`. Esse arquivo antigo não é apagado nem importado automaticamente: esta entrega começa com a base SQLite. Não edite o estoque diretamente por outro programa enquanto a loja estiver aberta, pois o catálogo também fica em memória durante a execução.
