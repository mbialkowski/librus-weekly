import SwiftUI

struct LoginView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    /// Shown as a sheet when adding another child.
    var isAddingChild = false

    @State private var login = ""
    @State private var password = ""
    @State private var isLoggingIn = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                if !isAddingChild {
                    Section {
                        VStack(alignment: .leading, spacing: 8) {
                            Image(systemName: "calendar.badge.clock")
                                .font(.system(size: 44))
                                .foregroundStyle(.tint)
                            Text("School Week")
                                .font(.largeTitle.bold())
                            Text("This week's grades, lessons, tests and announcements from Librus Synergia.")
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 8)
                    }
                }

                if !isAddingChild, let autoError = model.autoLoginError {
                    Section {
                        Label("Automatic login failed. \(autoError)", systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.orange)
                        Button("Try again") { Task { await model.autoLogin() } }
                    }
                }

                Section {
                    TextField("Login (e.g. 1234567u)", text: $login)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                        .onSubmit { Task { await submit() } }
                    Button {
                        Task { await submit() }
                    } label: {
                        if isLoggingIn {
                            ProgressView()
                        } else {
                            Text(isAddingChild ? "Add child" : "Log in")
                        }
                    }
                    .disabled(login.isEmpty || password.isEmpty || isLoggingIn)
                } header: {
                    Text("Synergia login")
                } footer: {
                    Text("Use the Synergia login, not the Konto Librus e-mail. Each child has its own parent login; you can add the others later and switch between them. Passwords are stored in the Keychain on this device only, because Librus sessions can't be renewed without them. Unofficial app, not affiliated with Librus.")
                }
            }
            .navigationTitle(isAddingChild ? "Add child" : "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if isAddingChild {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                }
            }
            .alert("Login failed", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("OK") { error = nil }
            } message: {
                Text(error ?? "")
            }
        }
    }

    private func submit() async {
        guard !login.isEmpty, !password.isEmpty, !isLoggingIn else { return }
        isLoggingIn = true
        defer { isLoggingIn = false }
        do {
            try await model.addAccount(login: login.trimmingCharacters(in: .whitespaces), password: password)
            password = ""
            if isAddingChild { dismiss() }
        } catch {
            self.error = error.localizedDescription
        }
    }
}
