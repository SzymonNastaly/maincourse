import SwiftUI

enum TimerTick {
    /// One shared schedule, so every countdown on screen ticks at the same instant.
    static let everySecond = PeriodicTimelineSchedule(from: Date(timeIntervalSinceReferenceDate: 0), by: 1)
}

enum TimerFormat {
    /// 9:58, 1:04:30
    static func clock(_ seconds: TimeInterval) -> String {
        CookingTimerClock.string(seconds)
    }

    /// 10 min, 1 hr 30 min, 45 sec, in the person's language.
    static func short(_ seconds: Int) -> String {
        Duration.seconds(seconds).formatted(.units(allowed: [.hours, .minutes, .seconds], width: .abbreviated))
    }

    /// 10 minutes, 1 hour 30 minutes, for VoiceOver.
    static func spoken(_ seconds: Int) -> String {
        Duration.seconds(seconds).formatted(.units(allowed: [.hours, .minutes, .seconds], width: .wide))
    }
}

/// A ring with the remaining time as a filled wedge, used by step chips, the dock
/// and the timer list.
struct TimerProgressPie: View {
    let progress: Double
    var isPaused = false
    var color = Color.mcAccent

    var body: some View {
        ZStack {
            Circle().stroke(self.color, lineWidth: 1.5)
            PieWedge(fraction: max(0, min(1, 1 - self.progress)))
                .fill(self.color)
                .padding(2.5)
                .opacity(self.isPaused ? 0.35 : 1)
            if self.isPaused {
                Image(systemName: "pause.fill")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(self.color)
            }
        }
        .accessibilityHidden(true)
    }

    private struct PieWedge: Shape {
        var fraction: Double

        func path(in rect: CGRect) -> Path {
            let center = CGPoint(x: rect.midX, y: rect.midY)
            var path = Path()
            guard self.fraction > 0 else { return path }
            path.move(to: center)
            path.addArc(
                center: center,
                radius: min(rect.width, rect.height) / 2,
                startAngle: .degrees(-90),
                endAngle: .degrees(-90 + 360 * self.fraction),
                clockwise: false
            )
            path.closeSubpath()
            return path
        }
    }
}
