use crate::compatibility::{convert_utf16_offsets, map_suggestion};
use crate::models::{DocumentMode, HarperConfig, HarperLint};
use harper_core::linting::{LintGroup, Linter};
use harper_core::spell::{FstDictionary, MergedDictionary, MutableDictionary};
use harper_core::{DictWordMetadata, Document};
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::{Arc, Mutex};

#[derive(uniffi::Object)]
pub struct HarperEngine {
    base_dict: Arc<FstDictionary>,
    active_dict: Mutex<Arc<MergedDictionary>>,
    linter: Mutex<LintGroup>,
    config_version: AtomicU32,
    document_mode: Mutex<DocumentMode>,
}

#[uniffi::export]
impl HarperEngine {
    #[uniffi::constructor]
    pub fn create() -> Self {
        let base_dict = FstDictionary::curated();
        let mut merged = MergedDictionary::new();
        merged.add_dictionary(base_dict.clone());

        let merged_arc = Arc::new(merged);

        let linter = Mutex::new(LintGroup::new_curated(
            merged_arc.clone(),
            harper_core::Dialect::American,
        ));
        Self {
            base_dict,
            active_dict: Mutex::new(merged_arc),
            linter,
            config_version: AtomicU32::new(0),
            document_mode: Mutex::new(DocumentMode::PlainEnglish),
        }
    }

    pub fn update_config(&self, config: HarperConfig) {
        let mut merged = MergedDictionary::new();
        merged.add_dictionary(self.base_dict.clone());

        if !config.user_dictionary.is_empty() {
            let mut user_dict = MutableDictionary::new();
            for word in &config.user_dictionary {
                user_dict.append_word_str(word, DictWordMetadata::default());
            }
            merged.add_dictionary(Arc::new(user_dict));
        }

        let merged_arc = Arc::new(merged);

        let mut linter_guard = self.linter.lock().unwrap();
        let mut new_linter = LintGroup::new_curated(merged_arc.clone(), config.dialect.into());
        for rule in config.disabled_rules {
            new_linter.config.set_rule_enabled(&rule, false);
        }
        *linter_guard = new_linter;

        *self.active_dict.lock().unwrap() = merged_arc;
        *self.document_mode.lock().unwrap() = config.document_mode;

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
        let dict = self.active_dict.lock().unwrap().clone();
        let mode = *self.document_mode.lock().unwrap();

        let doc = match mode {
            DocumentMode::PlainEnglish => Document::new_plain_english(&text, &*dict),
            DocumentMode::Markdown => Document::new_markdown_default(&text, &*dict),
        };

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
        assert!(
            lints_american.is_empty(),
            "color should NOT be flagged in American"
        );

        // "colour" is incorrect in American
        let lints_american_2 = engine.lint("The colour is nice".to_string(), "".to_string());
        assert!(
            !lints_american_2.is_empty(),
            "colour should be flagged in American"
        );

        // Change to British
        engine.update_config(HarperConfig {
            dialect: HarperDialect::British,
            document_mode: DocumentMode::PlainEnglish,
            disabled_rules: vec![],
            user_dictionary: vec![],
        });

        // "colour" is correct in British
        let lints_british = engine.lint("The colour is nice".to_string(), "".to_string());
        assert!(
            lints_british.is_empty(),
            "colour should NOT be flagged in British"
        );

        // "color" is incorrect in British
        let lints_british_2 = engine.lint("The color is nice".to_string(), "".to_string());
        assert!(
            !lints_british_2.is_empty(),
            "color should be flagged in British"
        );
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
            document_mode: DocumentMode::PlainEnglish,
            disabled_rules: vec!["SpellCheck".to_string()],
            user_dictionary: vec![],
        });

        let lints_disabled = engine.lint(text, "".to_string());
        assert!(
            lints_disabled.is_empty(),
            "Spelling error should be ignored when disabled"
        );
    }

    #[test]
    fn test_user_dictionary() {
        let engine = HarperEngine::create();
        let text = "This is a testt.".to_string();
        let lints = engine.lint(text.clone(), "".to_string());
        assert!(!lints.is_empty(), "Spelling error should be caught");

        engine.update_config(HarperConfig {
            dialect: HarperDialect::American,
            document_mode: DocumentMode::PlainEnglish,
            disabled_rules: vec![],
            user_dictionary: vec!["testt".to_string()],
        });

        let lints_disabled = engine.lint(text, "".to_string());
        assert!(
            lints_disabled.is_empty(),
            "Spelling error should be ignored when added to dictionary"
        );
    }

    #[test]
    fn test_markdown_mode() {
        let engine = HarperEngine::create();
        let text = "
# Title

Here is some code:
```python
print(\"teh\")
```
        "
        .to_string();

        // Under plain english, `print(\"teh\")` flags \"teh\" as a spelling error.
        let lints_plain = engine.lint(text.clone(), "".to_string());
        assert!(
            !lints_plain.is_empty(),
            "Should catch 'teh' under plain english"
        );

        engine.update_config(HarperConfig {
            dialect: HarperDialect::American,
            document_mode: DocumentMode::Markdown,
            disabled_rules: vec![],
            user_dictionary: vec![],
        });

        let lints_markdown = engine.lint(text, "".to_string());
        assert!(
            lints_markdown.is_empty(),
            "Should ignore 'teh' inside a markdown code block"
        );
    }
}
