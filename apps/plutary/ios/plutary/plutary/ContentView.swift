//
//  ContentView.swift
//  Plutary
//
//  Created by kjdrew on 9/17/25.
//

import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel: ReceiptsListViewModel
    private let persistenceController: PersistenceController

    init(persistenceController: PersistenceController) {
        _viewModel = StateObject(wrappedValue: ReceiptsListViewModel(receiptStore: persistenceController.receiptStore))
        self.persistenceController = persistenceController
    }

    var body: some View {
        ReceiptsListView(viewModel: viewModel, persistenceController: persistenceController)
    }
}

#Preview {
    ContentView(persistenceController: .preview)
}
