package edu.stanford.bmir.protege.web.server.search;

import com.google.gwt.regexp.shared.RegExp;
import edu.stanford.bmir.protege.web.server.owlapi.OWLAPIProject;
import edu.stanford.bmir.protege.web.shared.HasDispose;
import edu.stanford.bmir.protege.web.shared.search.SearchType;
import edu.stanford.bmir.protege.web.shared.search.SearchRequest;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;
import org.semanticweb.owlapi.util.ProgressMonitor;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 19/06/2013
 */
public class SearchManager implements HasDispose {

    private static final Logger logger = Logger.getLogger(SearchManager.class);

    private OWLAPIProject project;

    private ExecutorService service = Executors.newSingleThreadExecutor();

    private AtomicLong lastSearchId = new AtomicLong(0);

    private Set<SearchType> types = new HashSet<SearchType>();

    private List<SearchMetadata> searchMetadataCache = new ArrayList<SearchMetadata>();

    private final OWLOntologyChangeListener ontologyChangeListener;


    private final SearchMetadataImportManager importManager;

    private final List<ProgressMonitor> progressMonitors = new ArrayList<ProgressMonitor>();

    public SearchManager(OWLAPIProject project, SearchMetadataImportManager importManager) {
        this.project = project;
        this.importManager = importManager;
        types.add(SearchType.DISPLAY_NAME);
        types.add(SearchType.IRI);
        types.add(SearchType.ANNOTATION_VALUE);
        types.add(SearchType.LOGICAL_AXIOM);
        ontologyChangeListener = new OWLOntologyChangeListener() {
            public void ontologiesChanged(List<? extends OWLOntologyChange> changes) throws OWLException {
                markCacheAsStale();
            }
        };
        // TODO: CHANGE LISTENERS! + EVENT HANDLING
    }

    public void addProgressMonitor(ProgressMonitor pm) {
        progressMonitors.add(pm);
    }


    public void dispose() {
        // TODO: REMOVE CHANGE LISTENERS
    }

//    private void handleModelManagerEvent(OWLModelManagerChangeEvent event) {
//        if (isCacheMutatingEvent(event)) {
//            markCacheAsStale();
//        }
//    }
//
//    private boolean isCacheMutatingEvent(OWLModelManagerChangeEvent event) {
//        return event.isType(EventType.ACTIVE_ONTOLOGY_CHANGED) || event.isType(EventType.ENTITY_RENDERER_CHANGED) || event.isType(EventType.ENTITY_RENDERING_CHANGED);
//    }


    private void markCacheAsStale() {
        lastSearchId.set(0);
    }

    public boolean isSearchType(SearchType type) {
        return types.contains(type);
    }

    public void setTypes(Collection<SearchType> types) {
        this.types.clear();
        this.types.addAll(types);
        markCacheAsStale();
    }

    private void rebuildMetadataCache() {
        long t0 = System.currentTimeMillis();
        logger.info("Rebuilding search metadata cache...");
        fireIndexingStarted();
        try {
            searchMetadataCache.clear();
            List<SearchMetadataImporter> importerList = importManager.getImporters();
            for (SearchMetadataImporter importer : importerList) {
                SearchMetadataDB db = importer.getSearchMetadata(project, types);
                searchMetadataCache.addAll(db.getResults());
            }
            long t1 = System.currentTimeMillis();
            logger.info("    ...rebuilt search metadata cache in " + (t1 - t0) + " ms");
        }
        finally {
            fireIndexingFinished();
        }

    }


    public void performSearch(final SearchRequest searchRequest, final SearchResultHandler searchResultHandler) {
        if (lastSearchId.getAndIncrement() == 0) {
            service.submit(new Runnable() {
                public void run() {
                    rebuildMetadataCache();
                }
            });
        }
        service.submit(new SearchCallable(lastSearchId.incrementAndGet(), searchRequest, searchResultHandler));
    }


    private class SearchCallable implements Runnable {

        private long searchId;

        private SearchRequest searchRequest;

        private SearchResultHandler searchResultHandler;

        private SearchCallable(long searchId, SearchRequest searchRequest, SearchResultHandler searchResultHandler) {
            this.searchId = searchId;
            this.searchRequest = searchRequest;
            this.searchResultHandler = searchResultHandler;
        }

        public void run() {
//            logger.info("Starting search " + searchId + " (pattern: " + searchRequest.getSearchPattern().pattern() + ")");
            List<SearchResult> results = new ArrayList<SearchResult>();
            Pattern pattern = Pattern.compile(searchRequest.getSearchPattern().getSource());

            long searchStartTime = System.currentTimeMillis();
            fireSearchStarted();
            long count = 0;
            int total = searchMetadataCache.size();
            int percent = 0;
            for (SearchMetadata searchMetadata : searchMetadataCache) {
                if (!isLatestSearch()) {
                    // New search started
                    logger.info("    terminating search " + searchId + " prematurely");
                    return;
                }
                String text = searchMetadata.getSearchString();
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    results.add(new SearchResult(searchMetadata, RegExp.compile(pattern.pattern()), matcher.start(), matcher.end()));
                }
                count++;
                int nextPercent = (int) ((count * 100) / total);
                if (nextPercent != percent) {
                    percent = nextPercent;
                    fireSearchProgressed(percent, results.size());
                }
            }
            SearchManager.this.fireSearchFinished();
            long searchEndTime = System.currentTimeMillis();
            long searchTime = searchEndTime - searchStartTime;
            logger.info("    finished search " + searchId + " in " + searchTime + " ms (" + results.size() + " results)");
            fireSearchFinished(results, searchResultHandler);
        }

        private boolean isLatestSearch() {
            return searchId == lastSearchId.get();
        }

        private void fireSearchFinished(final List<SearchResult> results, final SearchResultHandler searchResultHandler) {
            if (SwingUtilities.isEventDispatchThread()) {
                searchResultHandler.searchFinished(results);
            }
            else {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        searchResultHandler.searchFinished(results);
                    }
                });
            }
        }


    }


    private void fireIndexingFinished() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (ProgressMonitor pm : progressMonitors) {
                    pm.setFinished();
                    pm.setIndeterminate(false);

                }
            }
        });
    }

    private void fireIndexingStarted() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (ProgressMonitor pm : progressMonitors) {
                    pm.setIndeterminate(true);
                    pm.setMessage("Searching");
                    pm.setStarted();
                }
            }
        });
    }

    private void fireSearchStarted() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (ProgressMonitor pm : progressMonitors) {
                    pm.setSize(100);
                    pm.setStarted();
                }
            }
        });
    }

    private void fireSearchProgressed(final long progress, final int found) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (ProgressMonitor pm : progressMonitors) {
                    pm.setProgress(progress);
                    if (found > 1 || found == 0) {
                        pm.setMessage(found + " results");
                    }
                    else {
                        pm.setMessage(found + " result");
                    }
                }
            }
        });
    }

    private void fireSearchFinished() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (ProgressMonitor pm : progressMonitors) {
                    pm.setFinished();
                }
            }
        });
    }

}
