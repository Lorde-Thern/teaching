# Exercises

Each one can be answered by reading or changing the code in `src/space/` and running the demos.

1. **A moon of a moon.** In `Handmade.java`, add a tiny body whose `parent` is `"vegeta_moon"`.
   Run lesson 03 and print its distance to Vegeta's Moon. Which method makes this work without
   any new code, and why?

2. **Break determinism.** In `SystemGenerator.generate`, change `r.nextFloat() > DENSITY` to
   `Math.random() > DENSITY`. Run lesson 04. What happens to "cells identical", and why? What
   would that mean for players in multiplayer?

3. **Bounce.** In `Transit.java`, set `ARRIVAL_OFFSET` to 80. Run lesson 07. What goes wrong,
   and which expression in `enterAtmosphere` already protects against it?

4. **Sky maths by hand.** A star 10,000 blocks away: how far away is it drawn, and by what factor
   is its radius multiplied? Check your answer with `Sky.skyDistance`.

5. **Weighted choice.** Out of 10,000 calls to `StarClass.random`, about how many red dwarfs (M)
   and blue giants (O) do you expect? Write a loop that counts them.

6. **Reverse it yourself.** Without looking at `Seeds.unmix`, explain why `h ^= h >>> 33` can be
   undone by doing the same thing again. (Hint: which bits of `h` does it change?)

7. **A new planet type.** Add a `CRYSTAL` type to `BodyType` and make `SystemGenerator.pickType`
   use it sometimes in the cold zone. Which lessons change their output?
