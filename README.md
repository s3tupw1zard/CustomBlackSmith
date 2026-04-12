# Custom Blacksmith

Custom Blacksmith is a Paper plugin for queued crafting and delivery gameplay. It combines recipe-based crafting, forging levels, timed production, and reward deliveries inside configurable GUIs.

## Features
- Player crafting GUI with recipe categories
- Timed crafting queue with progress tracking
- Forging XP and leveling system
- Unlockable crafting and delivery slots
- Delivery jobs with item, money, and point rewards
- Support for vanilla, ItemsAdder, and MMOItems content
- Optional Vault and PlayerPoints integration
- Admin GUIs and commands for recipes, deliveries, and player progression

## Requirements
- Paper 1.21+
- Java 17
- Optional integrations: Vault, PlayerPoints, ItemsAdder, MMOItems

## Commands
- `/blacksmith`
- `/blacksmithadmin [reload|delivery|open|set|give]`
- `/craftstatus`
- `/delivery`

## Permissions
- `customblacksmith.admin.open`
- `customblacksmith.admin.delivery`
- `customblacksmith.admin.create`
- `customblacksmith.admin.reload`
- `customblacksmith.admin.modifydata`
- `customblacksmith.player.open`
- `customblacksmith.player.status`
- `customblacksmith.player.delivery`

## Build
```bash
mvn clean package
```

## Author
Allync

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE).
