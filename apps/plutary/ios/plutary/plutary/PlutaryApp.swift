//
//  PlutaryApp.swift
//  Plutary
//
//  Created by kjdrew on 9/17/25.
//

import Foundation
import SwiftUI

@main
struct PlutaryApp: App {
    let persistenceController: PersistenceController

    init() {
        if ProcessInfo.processInfo.arguments.contains("--uitests") {
            let controller = PersistenceController(inMemory: true)
            persistenceController = controller
            Task {
                await UITestSeeder.seedIfNeeded(using: controller.receiptStore, arguments: ProcessInfo.processInfo.arguments)
            }
        } else {
            persistenceController = PersistenceController.shared
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView(persistenceController: persistenceController)
        }
    }
}
