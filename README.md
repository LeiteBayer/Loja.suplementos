# IronStore

Loja de suplementos desenvolvida para a disciplina de Programação Orientada a Objetos do curso de ADS. O backend em Java valida o estoque, calcula os descontos e registra as vendas em SQLite. O catálogo tem nove produtos, com busca, filtros e fichas de detalhes.

O checkout coleta os dados do cliente, o endereço e a forma de pagamento. A demonstração inclui Pix com QR Code, crédito e débito. O Clube VIP aplica 10% de desconto até R$ 700,00 e 15% acima desse subtotal enquanto a mensalidade estiver ativa.

## Executar

Com Java 17 ou superior, abra o terminal na pasta do projeto e execute o JAR. Mantenha a pasta `lib` ao lado dele.

```powershell
java -jar loja-suplementos.jar
```

Abra [localhost:8080](http://localhost:8080). O Java cria o banco com o catálogo inicial na primeira execução.

- [Instruções de execução e testes](LEIA_PRIMEIRO.md)
- [Guia de estudo e roteiro da apresentação](GUIA_APRESENTACAO.md)
