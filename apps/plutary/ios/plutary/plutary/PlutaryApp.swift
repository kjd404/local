//
//  PlutaryApp.swift
//  Plutary
//
//  Created by kjdrew on 9/17/25.
//

import CoreData
import SwiftUI

@main
struct PlutaryApp: App {
    let persistenceController = PersistenceController.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(\.managedObjectContext, persistenceController.container.viewContext)
        }
    }
}
