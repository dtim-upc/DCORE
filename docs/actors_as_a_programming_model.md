### Daniel Spiewak - 2019/05/21

Actors elevate the message and the message handlers to "first-class" status in the abstraction
But messages and handlers are almost never the hard things about a system.
The hard thing to understand and control is always the data flow, but actors make this actively
worse because the emergent properties become incredibly difficult to trace and reason about.

Actors are, to me, a failed abstraction in every case except when you're literally
designing a system which… sends messages. So a telecom. Applying them to a broader class
of things was a really neat idea, but frankly one which doesn't carry its weight in practice.

Rich Hickey had the best take I think I've ever heard on the actor model:
"Message passing is great for when you're sending messages."

Sometimes I wonder if FP has a marketing problem surrounding this sort of stuff.
Frameworks like Akka take something that is immensely complex and promises
"We make it simple! Let it crash. Don't worry about it; everything will work itself out."
It can't back up that promise (unsurprisingly), but you don't realize this until much later,
and the promise of simplicity makes it a very appealing choice.

FP, on the other hand, says "We'll help you understand how complex this thing is, and we'll
give you the tools for modeling and managing that complexity." That's actually a much stronger
and more sound promise, particularly if you want to future-proof your system, but it's a lot
less appealing on the surface.