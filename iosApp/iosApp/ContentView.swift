import SwiftUI
import Shared

/// Native SwiftUI screen driven by the shared Kotlin logic in `:shared`.
/// It reuses the very same Luhn check and brand detection that the Android app uses —
/// these are synchronous domain APIs, safe to call directly from Swift.
struct ContentView: View {
    @State private var cardNumber: String = ""

    private var digits: String { cardNumber.filter { $0.isNumber } }
    private var brand: String { CardBrand.companion.detect(number: digits).displayName }
    private var isValid: Bool { digits.count >= 12 && Luhn.shared.isValid(number: digits) }
    private var masked: String { digits.count >= 4 ? "•••• " + String(digits.suffix(4)) : "" }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Card")) {
                    TextField("Card number", text: $cardNumber)
                        .keyboardType(.numberPad)
                    HStack {
                        Text(brand)
                        Spacer()
                        Text(masked).foregroundColor(.secondary)
                    }
                    HStack {
                        Image(systemName: isValid ? "checkmark.circle.fill" : "xmark.circle")
                            .foregroundColor(isValid ? .green : .secondary)
                        Text(isValid ? "Valid card number (Luhn)" : "Enter a valid card number")
                    }
                }
                Section {
                    Text("Validated by shared Kotlin logic (:shared) — the same Luhn algorithm and brand detection as the Android app.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("CheckoutKMP")
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
