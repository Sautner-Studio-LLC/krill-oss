package com.krillforge.pi4j.servo

import com.krillforge.pi4j.pca9685.Pca9685Client

/** Wire one [Servo] to a single PCA9685 [channel], writing pulses directly (no batching). */
fun Pca9685Client.servo(
    channel: Int,
    profile: ServoProfile = ServoProfile(),
    frequencyHz: Double = 50.0,
): PulseServo = PulseServo(
    initialProfile = profile,
    writePulseUs = { us -> setChannel(channel, ServoMath.pulseUsToTicks(us, frequencyHz)) },
)
