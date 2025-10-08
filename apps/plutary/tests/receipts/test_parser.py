from __future__ import annotations

from plutary.receipts import HeuristicReceiptParser


def test_parser_extracts_total_and_merchant() -> None:
    parser = HeuristicReceiptParser()
    text = """
    River Cafe
    Subtotal 10.00
    TOTAL 12.34
    Thank you!
    """

    parsed = parser.parse(text)

    assert parsed.transaction is not None
    assert parsed.transaction.merchant == "River Cafe"
    assert parsed.transaction.total_amount_cents == 1234
    assert parsed.transaction.currency == "USD"


def test_parser_extracts_line_items() -> None:
    parser = HeuristicReceiptParser()
    text = """
    Bistro
    Coffee 3.50
    Croissant 4.25
    TOTAL 7.75
    """

    parsed = parser.parse(text)

    assert len(parsed.line_items) == 2
    assert parsed.line_items[0].description == "Coffee"
    assert parsed.line_items[0].amount_cents == 350


def test_parser_handles_split_total_lines() -> None:
    parser = HeuristicReceiptParser()
    text = """
    Domino's Pizza #7157
    Coupons / Adjustments
    Any Crust Any Toppings $10.90 $22.93-
    Total
    $27.85
    Amount Tendered
    $27.85
    Balance Due
    $0.00
    """

    parsed = parser.parse(text)

    assert parsed.transaction.total_amount_cents == 2785
    assert parsed.transaction.currency == "USD"


def test_parser_prefers_amount_tendered_synonym() -> None:
    parser = HeuristicReceiptParser()
    text = """
    Corner Market
    Subtotal
    $12.10
    Amount Tendered $15.00
    Balance Due $0.00
    """

    parsed = parser.parse(text)

    assert parsed.transaction.total_amount_cents == 1500
    assert parsed.transaction.currency == "USD"


def test_parser_handles_multiline_items_sample_02() -> None:
    parser = HeuristicReceiptParser()
    text = """
    Costco
    Wholesale
    Kirkland #08
    3M Member 11180380310
    Bottom of Basket BOB Count 0
    E 761486 Org Spaghetti
    12.89
    E 1680301 Enfamil Gent
    58.99
    E 1680301 Enfamil Gent
    58.99
    E 1680301 Enfamil Gent
    58.99
    Subtotal 189.86
    Tax 0.00
    Total
    189.86
    Visa Approved
    Amount 189.86
    Thank You Please Come Again
    """

    parsed = parser.parse(text)

    assert parsed.transaction is not None
    assert parsed.transaction.merchant == "Costco Wholesale Kirkland #08"
    assert parsed.transaction.total_amount_cents == 18986

    amounts_by_desc = {
        item.description: item.amount_cents for item in parsed.line_items
    }
    assert amounts_by_desc.get("Org Spaghetti") == 1289
    enfamil_items = [
        item for item in parsed.line_items if "Enfamil" in item.description
    ]
    assert enfamil_items, "Expected at least one Enfamil line item"
    assert enfamil_items[0].amount_cents == 5899


def test_parser_handles_multiline_items_sample_03() -> None:
    parser = HeuristicReceiptParser()
    text = """
    85°C
    Bakery
    Cafe
    To Go
    1 Marble Taro
    3.70
    1 Choco Croissant
    2.45
    1 M IC Boba Milk Tea
    4.85
    Subtotal 11.00
    Tax 0.49
    Total 11.49
    Credit Card 11.49
    Change 0.00
    """

    parsed = parser.parse(text)

    assert parsed.transaction is not None
    assert parsed.transaction.merchant == "85°C Bakery Cafe"
    assert parsed.transaction.total_amount_cents == 1149

    assert len(parsed.line_items) == 3
    marble = next(item for item in parsed.line_items if "Marble" in item.description)
    assert marble.quantity == 1
    assert marble.amount_cents == 370

    choco = next(item for item in parsed.line_items if "Croissant" in item.description)
    assert choco.quantity == 1
    assert choco.amount_cents == 245

    milk_tea = next(item for item in parsed.line_items if "Milk" in item.description)
    assert milk_tea.quantity == 1
    assert milk_tea.amount_cents == 485


def test_parser_reconstructs_merchant_and_items_sample_04() -> None:
    parser = HeuristicReceiptParser()
    text = """
    TRADER
    JOE'S
    #0162
    416 116th Ave NE Bellevue WA 98004
    Sale Transaction
    Open 9:00am to 9:00pm Daily
    T Yonder Palisades Blackberries 12.00
    4 @ $3.00
    4 for 11.99
    Tax 1.22
    Items in Transaction 4
    Balance to Pay 13.21
    Payment Card Purchase
    Total Purchase 13.21
    """

    parsed = parser.parse(text)

    assert parsed.transaction is not None
    assert parsed.transaction.merchant == "TRADER JOE'S #0162"
    assert parsed.transaction.total_amount_cents == 1321

    blackberry = next(
        item for item in parsed.line_items if "Blackberries" in item.description
    )
    assert blackberry.quantity == 4
    assert blackberry.amount_cents == 1199


def test_parser_handles_noisy_paddle_output_sample_02() -> None:
    parser = HeuristicReceiptParser()
    text = """
    EWHOLESALE
    Kirkland
    #08
    862:9 120th Avenur
    NE
    3H Member 111803880310
    xxxxxxxxxx*Bottom
    n of Basketxxxxxxxxxxx
    xxxxxxxxx*BOB Count 0
    *X********x***
    WWWW
    761486 0RG SPASHTTI
    12.89
    1680301 E
    ENFAMILAGENT
    58.99
    1680301 E
    ENFAMIL
    GENT
    58.99
    1680301
    ENFAMILAGENT
    58.99
    SUBTOTAL
    189.86
    TAX
    0.00
    TOTAL
    ****
    189.86
    XXXXXXXXXX'X'X6204
    H
    AID:
    A0000000031010
    Seq#
    4659
    App# :
    : 003701
    Visa
    Resp: APPROVED
    Tran ID#: 524500004659
    APPrOVED - Purchase
    AMOUNT: $189.86
    09/02/2025 11:13 8 4 55 10
    V1sa
    189.86
    CHANGE
    0.00
    TOTAL NUMBER OF ITEMS SOLD
    09702/2025 11:13 8 4 55 10
    21000800400552509021113
    OP#: 10 Name: Duans M
    Thank
    you!
    Come
    Please
    Again
    Whse:8 Trm:4 Trn:55 0P:10
    Items
    Sold: 4
    11:13
    3H 09/02/2025
    """

    parsed = parser.parse(text)

    assert parsed.transaction is not None
    assert parsed.transaction.merchant == "Costco Wholesale Kirkland #08"
    assert parsed.transaction.total_amount_cents == 18986

    item_amounts = sorted(item.amount_cents for item in parsed.line_items)
    assert item_amounts.count(1289) == 1
    assert item_amounts.count(5899) == 3


def test_parser_handles_noisy_paddle_output_sample_03() -> None:
    parser = HeuristicReceiptParser()
    text = """
    85C Bakery Cafe
    (425)434-4768
    To Go
    Chk: 02143
    0D3481
    Owen
    0108-02
    2025/09/18
    10:48:31
    Marble
    Taro
    3.70
    Choco Croissant
    2.45
    M IC Boba Mi1k
    Tea
    4.85 T
    SUBTOTAL
    11.00
    TAX
    0.49
    TOTAL
    11.49
    CreditCard
    11.49
    CHANGE
    0.00
    VISA
    11.49
    XXXXXXXXXXXX6204
    Method: EMV CONTACTLESS
    Ref#:
    526100942726
    Auth#:
    048181
    AID:
    AC000000031010
    Order:
    4N6D2KQ7WYA8A
    YOUR
    ORDER
    R NUMBER
    2143
    THANK
    < YOU FOR VISITING
    For a
    section of your
    the
    "Add Missing Points".
    scan. the QR code below. Or go to
    app a
    and
    r "Missed Visit Code.
    the code
    e below under
    code : 055PHBWK3HGR
    """

    parsed = parser.parse(text)

    assert parsed.transaction is not None
    assert parsed.transaction.merchant == "85°C Bakery Cafe"
    assert parsed.transaction.total_amount_cents == 1149

    descriptions = {item.description for item in parsed.line_items}
    assert any("Marble" in desc for desc in descriptions)
    assert any("Choco" in desc for desc in descriptions)
    assert any("Tea" in desc for desc in descriptions)


def test_parser_handles_noisy_paddle_output_sample_04() -> None:
    parser = HeuristicReceiptParser()
    text = """
    TRADER
    JOE'S
    0
    4
    416 116th Ave. NF
    98004
    Store #0162
    425-454-9799
    UPEN 9:OOAM IO 9:OOPM DAILY
    SALE IRANSACTION
    T YONDER PALISADES BLACKBE
    $12.00
    4 @ $3.00
    4 for 11.99
    $0.01
    Tax:
    $11.99 0 10.2%
    $1.22
    Items
    n Transaction:4
    Halance: to pay.
    $13.21
    VISA
    $13.21
    PAYMENT CARD PURCHASE TRANSACT ION
    CUSTOMER COPY
    VISA CREDIT
    ************3171
    CONTACILESS
    type:
    Auth Coda
    08403)
    3
    MID: 1
    *******27013
    TID:
    ****6000
    TOTAL PURCHASE
    $13.21
    No Cardho Ider Verificat ion.
    Please retain for your records
    P,Ali
    STORE
    TILL
    TRANS
    DATE
    0162
    49307
    09-17-2025 20:36
    THANK YOU FOR SHOPPING AT
    IRADER JOE'S
    www.traderjoes.com
    """

    parsed = parser.parse(text)

    assert parsed.transaction is not None
    assert parsed.transaction.merchant == "TRADER JOE'S #0162"
    assert parsed.transaction.total_amount_cents == 1321

    items = parsed.line_items
    assert len(items) == 1
    assert "Blackbe" in items[0].description
    assert items[0].quantity == 4
    assert items[0].amount_cents == 1199
