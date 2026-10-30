import XCTest

final class LaunchTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    func testFirstLaunchOffersOwnComputerPairing() throws {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.staticTexts["Tus computadoras"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Escanear QR de mi PC")).firstMatch.exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = "Ayudix-iPhone-primera-vinculacion"; screenshot.lifetime = .keepAlways; add(screenshot)
    }
    func testFilesDoesNotShowAnotherPersonsComputer() throws {
        let app = XCUIApplication(); app.launch()
        let tab = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Archivos")).firstMatch
        XCTAssertTrue(tab.waitForExistence(timeout: 20)); tab.tap()
        XCTAssertTrue(app.staticTexts["Elige tu computadora en Mis PC para ver sus carpetas autorizadas."].waitForExistence(timeout: 10))
        XCTAssertEqual(app.state, .runningForeground)
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = "Ayudix-iPhone-archivos-sin-vincular"; screenshot.lifetime = .keepAlways; add(screenshot)
    }
    func testChatRequiresAnExplicitPairing() throws {
        let app = XCUIApplication(); app.launch()
        let tab = app.buttons.matching(NSPredicate(format: "label == %@ OR label BEGINSWITH %@", "Ayudix", "Ayudix,")).firstMatch
        XCTAssertTrue(tab.waitForExistence(timeout: 20)); tab.tap()
        XCTAssertTrue(app.staticTexts["Vincula tu PC en Mis PC para conversar con Ayudix."].waitForExistence(timeout: 10))
        XCTAssertEqual(app.state, .runningForeground)
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = "Ayudix-iPhone-chat-sin-vincular"; screenshot.lifetime = .keepAlways; add(screenshot)
    }
}
