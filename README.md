# Trixnity VK Bridge

This is a personal bridge based on older version of [trixnity-bridge](https://github.com/HeroBrine1st/trixnity-bridge).

Development of this bridge is ignited even before trixnity-bridge framework became an idea: first commit - January 2023,
working bridge - January 2025, and it could be more
without [MSC3916](https://github.com/matrix-org/matrix-spec-proposals/pull/3916) which
rendered [mx-puppet-vk](https://github.com/HeroBrine1st/mx-puppet-vk) inoperable for everything but text messages.
The history is squashed here from initial commit and to the point where trixnity-bridge became its own project.

Initially it was thought that application services are the middle level I needed (as all other libraries either gave me
too much abstractions or don't have much docs). I was wrong, and this led to trixnity-bridge, the only framework that
gives (or will give - hello [MSC1772](https://github.com/matrix-org/matrix-spec-proposals/pull/1772)) you the power of
writing mid-level code combined with ease of writing high-level logic.

So, this bridge is pretty ancient: it has its own repository implementation based on sqldelight and this implementation
uses a controversial message repository interface (which had `sender` field back in the days). As such, this bridge is
considered **legacy**. However, it ls legacy only because trixnity-bridge is cutting edge, and it won't be an issue to
update it to latest trixnity-bridge.

Let's go with features. This bridge has actor configuration
in [configuration file](vk-bridge/src/main/resources/application.conf), but don't be confused: it only adds new actors
when there's none. So, you'll need to manually edit database if need to change something.

There is a migration mechanism as this bridge is built to successfully
replace [mx-puppet-vk](https://github.com/HeroBrine1st/mx-puppet-vk) - you'll need some experience with SQLite and regex
substitutions (or some python magic?) to transform mx-puppet-vk database into something this bridge will understand. The
migration is idempotent, which was confirmed while I was fixing it to handle all possible corner cases, and the
mx-puppet-vk is likely not rendered inoperable after that, however mx-puppet-bridge is such a big piece of code for me
that I don't entirely know how it works! It might use `GET /_matrix/client/v3/rooms/{roomId}/joined_members` and be
confused, so get your backups ready.

This bridge has new feature (compared to mx-puppet-vk) powered by unique library (included in this repo): it handles
message deletions. It does not redact messages (I'm waiting on message attachments to do it properly), but it still
indicates that message is deleted. The library is probably the only library that goes with user-first approach and does
not try to fit incomplete user events on bot events with full info, which leads to the most low-level access possible.

This bridge has slightly contriversial feature of notifying you with [ntfy](https://ntfy.sh/) if something goes wrong.
Unfortunately, it is not optional. Used as a last resort so that I'm not blind if this fails to work. However, it seems
it is the most stable and reliable project I've ever wrote thanks to my habits of safe code. My deployment can even be
considered mission-critical in some sense, so I need instant feedback being it error message or usable message
transport.