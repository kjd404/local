from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional


@dataclass(frozen=True)
class Persona:
    name: str
    system_prompt: str
    avatar_url: Optional[str] = None
    summary: Optional[str] = None


_BUILTIN: Dict[str, Persona] = {
    # Pragmatic defaults
    "Alex": Persona(
        name="Alex",
        system_prompt=(
            "You are Alex, a calm, left-leaning policy analyst."
            " You reason in plain language, cite concrete examples, and avoid rhetorical flourish."
        ),
        summary="Left-leaning policy analyst; plain, evidence-led style.",
    ),
    "Riley": Persona(
        name="Riley",
        system_prompt=(
            "You are Riley, a thoughtful, right-leaning policy analyst."
            " You value prudence, incentives, and practical trade-offs without grandstanding."
        ),
        summary="Right-leaning policy analyst; pragmatic, trade-off aware.",
    ),
    # Classical
    "Demosthenes": Persona(
        name="Demosthenes",
        system_prompt=(
            "You are Demosthenes, the Athenian statesman and orator."
            " Argue with structured reasoning and civic duty."
        ),
        summary="Athenian orator; structured, civic-minded rhetoric.",
    ),
    "Aeschines": Persona(
        name="Aeschines",
        system_prompt=(
            "You are Aeschines, rival of Demosthenes."
            " Argue with sharp rebuttals and legalistic framing."
        ),
        summary="Demosthenes’ rival; sharp, legalistic framing.",
    ),
    "Isocrates": Persona(
        name="Isocrates",
        system_prompt=(
            "You are Isocrates, an educator and rhetorician."
            " Emphasize balance with polished prose."
        ),
        summary="Educator; balanced, polished prose.",
    ),
    "Gorgias": Persona(
        name="Gorgias",
        system_prompt=(
            "You are Gorgias, the sophist."
            " Favor stylistic flourish and probing questions."
        ),
        summary="Sophist; stylistic flourish and paradox.",
    ),
    "Lysias": Persona(
        name="Lysias",
        system_prompt=(
            "You are Lysias, the logographer."
            " Use plain, direct style with tight arguments."
        ),
        summary="Logographer; plain, direct and practical.",
    ),
}


def _default_path() -> Path:
    env = os.getenv("ATHENA_PERSONAS_FILE", "").strip()
    if env:
        return Path(env)
    # Attempt to locate default bundled file
    here = Path(__file__).resolve().parent
    return here / "resources" / "personas.yaml"


def load_personas(path: Optional[Path | str] = None) -> Dict[str, Persona]:
    """Load personas from YAML; fallback to built-in set on failure.

    YAML format:
      personas:
        Alex:
          system_prompt: "..."
          summary: "..."
          avatar_url: "..."
    """
    p = Path(path) if path else _default_path()
    try:
        import yaml  # type: ignore
    except Exception:
        return dict(_BUILTIN)

    try:
        if not p.exists():
            return dict(_BUILTIN)
        data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        items = data.get("personas", {})
        loaded: Dict[str, Persona] = {}
        for name, cfg in items.items():
            if not isinstance(cfg, dict):
                continue
            sp = str(cfg.get("system_prompt", "")).strip()
            if not sp:
                continue
            loaded[name] = Persona(
                name=name,
                system_prompt=sp,
                avatar_url=(
                    str(cfg.get("avatar_url")) if cfg.get("avatar_url") else None
                ),
                summary=(str(cfg.get("summary")) if cfg.get("summary") else None),
            )
        return loaded or dict(_BUILTIN)
    except Exception:
        return dict(_BUILTIN)


_CACHE: Optional[Dict[str, Persona]] = None


def get_personas() -> Dict[str, Persona]:
    global _CACHE
    if _CACHE is None:
        _CACHE = load_personas()
    return _CACHE
