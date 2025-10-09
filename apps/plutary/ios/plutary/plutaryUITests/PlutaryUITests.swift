//
//  PlutaryUITests.swift
//  PlutaryUITests
//
//  Created by kjdrew on 9/17/25.
//

import XCTest

final class PlutaryUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSeededReceiptsAppearInList() throws {
        let app = launchApp(withFixture: .sample)

        let sampleCell = app.staticTexts["Sample Deli"]
        XCTAssertTrue(sampleCell.waitForExistence(timeout: 3), "Seeded receipt should appear in the list")

        sampleCell.tap()
        XCTAssertTrue(app.staticTexts["Notes"].exists, "Detail view should show metadata section")
    }

    func testManualEntryCreatesReceipt() throws {
        let app = launchApp(withFixture: .empty)

        app.buttons["Add Receipt"].tap()
        app.buttons["Enter manually"].tap()

        let merchantField = app.textFields["Merchant Name"]
        XCTAssertTrue(merchantField.waitForExistence(timeout: 2))
        merchantField.tap()
        merchantField.typeText("UITest Coffee")

        let totalField = app.textFields["Total Amount"]
        totalField.tap()
        totalField.typeText("9.75")

        app.buttons["Save"].tap()

        let newReceipt = app.staticTexts["UITest Coffee"]
        XCTAssertTrue(newReceipt.waitForExistence(timeout: 3), "Newly created receipt should appear in the list")
    }

    private func launchApp(withFixture fixture: Fixture) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["--uitests", "--fixture", fixture.rawValue]
        app.launch()
        return app
    }

    private enum Fixture: String {
        case sample
        case empty
    }
}
