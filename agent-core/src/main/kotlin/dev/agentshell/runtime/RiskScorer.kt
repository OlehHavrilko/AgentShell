package dev.agentshell.runtime

class RiskScorer {
    fun score(toolName: String, args: String, isNewRepo: Boolean = false): Int {
        var score = 0

        score += when (toolName) {
            "git_push" -> 40
            "shell_exec" -> 30
            "file_write", "file_patch" -> 20
            else -> 10
        }

        val riskyPatterns = listOf("-rf", "--force", "DROP TABLE", "chmod 777", "curl | bash")
        riskyPatterns.forEach { pattern ->
            if (args.contains(pattern, ignoreCase = true)) {
                score += 30
            }
        }

        if (isNewRepo) {
            score += 10
        }

        return score.coerceIn(0, 100)
    }

    fun requiresApproval(score: Int, threshold: Int = 60): Boolean = score >= threshold
}
