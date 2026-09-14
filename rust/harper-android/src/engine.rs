use crate::compatibility::{convert_utf16_offsets, map_suggestion};
use crate::models::{AnalysisMetadata, EditOperation, HarperConfig, HarperLint, HarperSuggestion};
use harper_core::linting::{LintGroup, Linter};
use harper_core::spell::FstDictionary;
use harper_core::Document;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Instant;

#[derive(uniffi::Object)]
pub struct HarperEngine {
    dict: Arc<FstDictionary>,
    linter: Mutex<LintGroup>,
    config_version: AtomicU32,
}

#[uniffi::export]
impl HarperEngine {
    #[uniffi::constructor]
    pub fn create() -> Self {
        let dict = FstDictionary::curated();
        let linter = Mutex::new(LintGroup::new_curated(
            dict.clone(),
            harper_core::Dialect::American,
        ));
        Self { dict, linter, config_version: AtomicU32::new(0) }
    }

    pub fn update_config(&self, config: HarperConfig) {
        let mut linter_guard = self.linter.lock().unwrap();
        let mut new_linter = LintGroup::new_curated(self.dict.clone(), config.dialect.into());
        for rule in config.disabled_rules {
            new_linter.config.set_rule_enabled(&rule, false);
        }
        *linter_guard = new_linter;
        self.config_version.fetch_add(1, Ordering::SeqCst);
    }

    pub fn get_config_version(&self) -> u32 {
        self.config_version.load(Ordering::SeqCst)
    }

    pub fn version(&self) -> String {
        env!("CARGO_PKG_VERSION").to_string()
    }

    pub fn schema_version(&self) -> String {
        "1".to_string()
    }

    pub fn capabilities(&self) -> Vec<String> {
        vec!["grammar".to_string(), "spelling".to_string()]
    }

    pub fn lint(&self, text: String, _language: String) -> Vec<HarperLint> {
        let doc = Document::new_plain_english(&text, &*self.dict);
        let mut linter_guard = self.linter.lock().unwrap();
        let lints = linter_guard.lint(&doc);
        drop(linter_guard);

        lints
            .into_iter()
            .enumerate()
            .map(|(i, lint)| {
                let (start_utf16, end_utf16) =
                    convert_utf16_offsets(&text, lint.span.start, lint.span.end);

                HarperLint {
                    issue_id: format!("issue_{}", i),
                    start_utf16,
                    end_utf16,
                    message: lint.message,
                    rule_id: None, // harper-core Lint does not expose a stable rule_id in 2.10.0 easily, we can leave None
                    suggestions: lint
                        .suggestions
                        .into_iter()
                        .enumerate()
                        .filter_map(|(s_idx, s)| map_suggestion(s, s_idx))
                        .collect(),
                }
            })
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::HarperDialect;

    #[test]
    fn test_dialect_change() {
        let engine = HarperEngine::create();
        
        // "color" is correct in American
        let lints_american = engine.lint("The color is nice".to_string(), "".to_string());
        
        // "colour" is incorrect in American
        let lints_american_2 = engine.lint("The colour is nice".to_string(), "".to_string());
        assert!(!lints_american_2.is_empty(), "colour should be flagged in American");

        // Change to British
        engine.update_config(HarperConfig {
            dialect: HarperDialect::British,
            document_mode: "plain_english".to_string(),
            disabled_rules: vec![],
        });

        // "colour" is correct in British
        let lints_british = engine.lint("The colour is nice".to_string(), "".to_string());
        assert!(lints_british.is_empty(), "colour should NOT be flagged in British");
        
        // "color" is incorrect in British
        let lints_british_2 = engine.lint("The color is nice".to_string(), "".to_string());
        assert!(!lints_british_2.is_empty(), "color should be flagged in British");
    }

    #[test]
    fn test_disable_rule() {
        let engine = HarperEngine::create();
        
        let text = "This is a testt.".to_string(); // "testt" is a spelling error

        let lints = engine.lint(text.clone(), "".to_string());
        assert!(!lints.is_empty(), "Spelling error should be caught");

        // Disable spelling
        engine.update_config(HarperConfig {
            dialect: HarperDialect::American,
            document_mode: "plain_english".to_string(),
            disabled_rules: vec!["SpellCheck".to_string()],
        });

        let lints_disabled = engine.lint(text, "".to_string());
        assert!(lints_disabled.is_empty(), "Spelling error should be ignored when disabled");
    }
}
