---
---
# qcBlocks scripting reference

This is the full guide to the `/qcb` script format. If you just want an AI to write
scripts for you, see [AI_PROMPT.md](AI_PROMPT.md).

## The basics

You run one command in chat:

```
/qcb <script>
```

It reads the script, then places a line of pre-filled command blocks next to you and
wires them into a chain. You do not place any blocks yourself, and you do not set
repeat/chain/impulse modes by hand. It is all done for you.

A few things to know before the syntax:

- You need cheats on (operator, permission level 2). In singleplayer that means
  "Allow Cheats" is on for the world.
- Only a player can run it, not a command block or the server console.
- qcBlocks raises the chat character limit, so you can paste a long script as a
  single chat line and it will not get cut off.
- Blocks are placed starting near you and run in a straight line. If something is
  already in those spots, it gets replaced, so leave yourself some empty space.

## Script shape

A script has two parts:

```
<header>   <statement> ; <statement> ; <statement> ...
```

- The **header** is optional. It sets where the chain starts and which way it runs.
- Each **statement** becomes one command block. Statements are separated by a
  semicolon `;` or a new line.

Because `;` and new lines split statements, do not put either one inside a command.

## The header

The header is everything before your first statement. It is made of tokens
separated by spaces, commas, or semicolons. There are two kinds of token.

### Direction the chain runs

```
dir=<direction>
```

This sets the way the chain of blocks runs from the starting block. If you leave it
out, the chain runs in the first direction you gave a start offset to, and if you
gave none, it runs forward (the way you are facing).

### Where the chain starts

```
<direction>=<number>
<direction>
```

This offsets the starting block away from you. You can give more than one and they
stack. Writing just the direction with no number means one block.

By default the first block is placed one block in front of you. If you want it to
start on your own block instead, set `forward=0`.

Examples of headers:

```
dir=forward down=2          start 1 ahead and 2 down, chain runs forward
dir=up                      stack the chain straight up
right=3                     start 3 blocks to your right, chain runs right
forward=0 dir=north         start on your block, chain runs north
```

## Directions

| Name | Short | Meaning |
|------|-------|---------|
| forward | f | the way you are facing |
| back | b | behind you |
| left | l | to your left |
| right | r | to your right |
| up | u | straight up |
| down | d | straight down |
| north | n | absolute north |
| south | s | absolute south |
| east | e | absolute east |
| west | w | absolute west |

forward, back, left, and right are based on the way you are looking (snapped to the
nearest of north, south, east, west). The compass names and up/down are absolute.

## Statements

Each statement is one command block. The full form is:

```
<kind>[<flags>]: <command>
```

### Kind

| Letter | Block | Runs |
|--------|-------|------|
| `r` | repeating command block | every tick, on its own |
| `c` | chain command block | when the block feeding into it runs |
| `i` | impulse command block | once each time it is powered |

A normal chain is one `r:` block at the front followed by `c:` blocks. The repeating
block drives the chain every tick, and each chain block runs in order after it.

### Flags

Flags go in square brackets, separated by commas. All are optional.

| Flag | Effect |
|------|--------|
| `cond` | conditional. The block only runs if the block before it succeeded. |
| `needs` | needs redstone. The block is off until it gets a redstone signal. |
| `auto` | always active. This is the default, so you rarely need to write it. |

`conditional` is the same option as in the vanilla command block screen, and `needs`
is the opposite of "Always Active".

### Writing the command

Write the command the way you would type it into a command block, with no leading
slash. For example `say hi`, not `/say hi`.

### Shorthand

If you skip the `kind:` prefix and just write a command, qcBlocks fills it in for
you. The first plain command becomes a repeating block and every plain command after
it becomes a chain block. So these two scripts do the same thing:

```
/qcb r: time set day; c: weather clear
/qcb time set day; weather clear
```

One detail to remember: an impulse block with no flags is set to need redstone by
default, since an impulse block that fires once on its own is rarely what you want.

## Worked examples

A simple repeating announcer:

```
/qcb r: say still running
```

Set the time and clear the weather, as a clean chain in front of you:

```
/qcb dir=forward; r: time set day; c: weather clear
```

Only act when a player is nearby, using a conditional follow-up:

```
/qcb r: execute if entity @a[distance=..8]; c[cond]: say someone is close
```

Build the chain going up instead of along the ground, starting two blocks above you:

```
/qcb dir=up up=2; r: fill ~ ~ ~ ~ ~ ~ air; c: setblock ~ ~-1 ~ glass
```

A one-shot block you trigger with a lever or button:

```
/qcb i: tp @a 0 100 0
```

## What you see when it works

On success you get a message like:

```
qcb: placed 3 command blocks [start 12, 64, -7, chain north]
```

If something is wrong with the script (an unknown direction, a bad flag, or no
commands at all) it tells you what to fix and places nothing.

## Common mistakes

- Using a semicolon inside a command. The semicolon splits statements, so the
  command gets cut in half. Rework the command so it does not need one.
- Adding a leading slash to a command. Write `say hi`, not `/say hi`.
- Forgetting that a plain first command becomes a repeating block. Add an explicit
  `i:` or `c:` prefix if that is not what you want.
- Standing where the blocks will be placed. Give the chain empty space, or use a
  start offset to move it off you.
