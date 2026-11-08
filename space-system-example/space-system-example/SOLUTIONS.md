# Answers

1. **A moon of a moon.** `StarSystem.bodyPosition` is recursive: a body's centre is its parent's
   position, and the parent's position is found the same way. So any depth of nesting works.
   Example: `Body.handmade(id, "pebble", "Pebble", BodyType.BARREN, "vegeta_moon", Orbit.of(60, 0, 2, 0), 6, null)`.
   Its distance to Vegeta's Moon is always 60.

2. **Break determinism.** `Math.random()` isn't seeded, so each run, and each Galaxy object,
   gets different answers. "Cells identical" drops to roughly half. In multiplayer, every player's
   game would draw different planets in different places from the server's, and travel would put
   people inside planets they can't see.

3. **Bounce.** Arrival would be at 320 + 80 = 400, above the exit line at 384. The next tick the
   player "leaves orbit" again and bounces back to space. `Math.min(ARRIVAL_OFFSET, EXIT_MARGIN - 16)`
   caps arrival at 368, under the exit line.

4. **Sky maths.** `skyDistance(10000) = 10 + 8 * ln(1 + 500) ≈ 59.7`. The factor is
   `59.7 / 10000 ≈ 0.006`, so the radius is multiplied by about 0.006.

5. **Weighted choice.** The weights add up to 87. M has weight 28, so about 10000 × 28 / 87 ≈ 3,218.
   O has weight 1, so about 115.

6. **Reverse it yourself.** `h >>> 33` is shifted 33 places, so `h ^= h >>> 33` only changes the low
   31 bits; the top 33 bits stay the same. Doing it again XORs those low bits with the same top bits
   once more, which cancels out (because `a ^ b ^ b == a`).

7. **A new planet type.** Lessons 04 and 06 change (lesson 07 only uses the hand-made Vegeta system). Adding
   an option changes what `r.nextInt(options.length)` returns, so every system after that point in a cell
   can come out differently. That's a useful lesson in itself: in a seeded generator, changing the
   order or number of random calls changes the whole galaxy.
