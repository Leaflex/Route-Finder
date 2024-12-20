package com.example.project2;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.project2.util.RouteUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.example.project2.adapter.RouteAdapter;
import com.example.project2.model.Route;

/**
 * Activity for displaying the dashboard view with a list of different routes from the database
 */
public class MainActivity extends AppCompatActivity implements
        RouteAdapter.OnRouteSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int LIMIT = 50;

    /**
     * Variables for the recycler view
     */
    private RecyclerView mRoutesRecycler;
    private ViewGroup mEmptyView;

    /**
     * Variables for Firestore
     */
    private FirebaseFirestore mFirestore;
    private Query mQuery;
    private RouteAdapter mAdapter;

    /**
     * Variables for elements in the activity_dashboard.xml layout.
     */
    private Button filterRatingButton;
    private Button filterLocationButton;
    private Button filterDifficultyButton;
    private Button filterSlopeButton;
    private EditText searchLabel;
    private Button buttonCreateRoutes;
    private Button selectedFilterButton; // To track the selected filter button

    /**
     * Initializes the activity.
     * @param savedInstanceState The saved state of the activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Get the current user from FirebaseAuth
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Extract the username from the email
        String username = "Guest"; // Default value in case the user is null or email is missing
        if (currentUser != null && currentUser.getEmail() != null) {
            String email = currentUser.getEmail();
            username = email.substring(0, email.indexOf('@')); // Extract the part before '@'
        }

        // Set the username in the TextView
        TextView usernameTitle = findViewById(R.id.username_title);
        usernameTitle.setText(username);

        // Initialize Firestore
        mFirestore = FirebaseFirestore.getInstance();

        // Set up Firestore query to fetch routes for community-made routes
        mQuery = mFirestore.collection("community_routes")
                .orderBy("avgRating", Query.Direction.DESCENDING)
                .limit(LIMIT);

        // Initialize RecyclerView
        mRoutesRecycler = findViewById(R.id.recycler_view);
        mEmptyView = findViewById(R.id.view_empty);
        initRecyclerView();

        // Initialize filter buttons and search bar
        filterRatingButton = findViewById(R.id.filter_rating);
        filterLocationButton = findViewById(R.id.filter_location);
        filterDifficultyButton = findViewById(R.id.filter_difficulty);
        filterSlopeButton = findViewById(R.id.filter_slope);
        searchLabel = findViewById(R.id.search_label);

        // Set button click listeners
        setUpFilters();

        // Set search bar listener
        setUpSearch();

        // Set up Test Routes button
//        Button testRoutesButton = findViewById(R.id.button_test_routes);
//        testRoutesButton.setOnClickListener(v -> generateRoutes());

        // When "Create Route" button is pressed, go to view that allows user to create a route
        buttonCreateRoutes = findViewById(R.id.button_create_routes);
        buttonCreateRoutes.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateRouteActivity.class);
            startActivity(intent);
        });

        // Hide the create route button initially (for community view)
        buttonCreateRoutes.setVisibility(View.GONE);

        // Set up BottomNavigationView
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this::onNavigationItemSelected);
    }

    /**
     * Handles navigation item selection in the BottomNavigationView.
     * @param item The selected navigation item.
     * @return True if the selection is handled, false otherwise.
     */
    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.nav_community){
            switchToCommunityView();
            return true;
        }
        else if(item.getItemId() == R.id.nav_your_routes){
            switchToYourRoutesView();
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Switches to the community view, where routes are displayed from the community_routes collection.
     */
    private void switchToCommunityView() {
        mQuery = mFirestore.collection("community_routes")
                .orderBy("avgRating", Query.Direction.DESCENDING)
                .limit(LIMIT);
        mAdapter.setQuery(mQuery);

        // Hide the "Create Route" button
        buttonCreateRoutes.setVisibility(View.GONE);
    }

    /**
     * Switches to the user's routes view, where routes are displayed from the user_routes collection.
     */
    private void switchToYourRoutesView() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            mQuery = mFirestore.collection("user_routes")
                    .orderBy("avgRating", Query.Direction.DESCENDING)
                    .limit(LIMIT);
            mAdapter.setQuery(mQuery);

            // Show the "Create Route" button
            buttonCreateRoutes.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Checks if the current view is the community view.
     * @return True if the current view is the community view, false otherwise.
     */
    private boolean isCommunityView() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        return bottomNavigationView.getSelectedItemId() == R.id.nav_community;
    }

    /**
     * Initializes the RecyclerView for displaying routes.
     */
    private void initRecyclerView() {
        if (mQuery == null) {
            Log.w(TAG, "No query, not initializing RecyclerView");
        }

        // Create a new adapter
        mAdapter = new RouteAdapter(mQuery, this) {
            @Override
            protected void onDataChanged() {
                // Show or hide RecyclerView based on query results
                if (getItemCount() == 0) {
                    mRoutesRecycler.setVisibility(View.GONE);
                    mEmptyView.setVisibility(View.VISIBLE);
                } else {
                    mRoutesRecycler.setVisibility(View.VISIBLE);
                    mEmptyView.setVisibility(View.GONE);
                }
            }

            @Override
            protected void onError(FirebaseFirestoreException e) {
                // Show error message
                Snackbar.make(findViewById(android.R.id.content),
                        "Error: Check logs for details.", Snackbar.LENGTH_LONG).show();
            }
        };

        // Set up RecyclerView with a grid layout (3 columns for the grid)
        mRoutesRecycler.setLayoutManager(new GridLayoutManager(this, 3));
        mRoutesRecycler.setAdapter(mAdapter);
    }

    /**
     * Set onClickListeners for each filter button, and determines the current filter
     */
    private void setUpFilters() {
        filterRatingButton.setOnClickListener(v -> {
            applyFilter(Route.FIELD_AVG_RATING);
            setSelectedFilter(filterRatingButton);
        });
        filterLocationButton.setOnClickListener(v -> {
            applyFilter(Route.FIELD_CITY);
            setSelectedFilter(filterLocationButton);
        });
        filterDifficultyButton.setOnClickListener(v -> {
            applyFilter(Route.FIELD_DIFFICULTY);
            setSelectedFilter(filterDifficultyButton);
        });
        filterSlopeButton.setOnClickListener(v -> {
            applyFilter(Route.FIELD_SLOPE);
            setSelectedFilter(filterSlopeButton);
        });
    }

    /**
     * Sets the currently selected filter button by changing backgroundTint
     * @param button The button currently selected by the user
     */
    private void setSelectedFilter(Button button) {
        if (selectedFilterButton != null) {
            selectedFilterButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.greyLight));
        }
        selectedFilterButton = button;
        selectedFilterButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.purple_200));
    }

    /**
     * Format the search bar and filter routes based on the filter selected when the user types in the search bar
     */
    private void setUpSearch() {
        searchLabel.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearch(s.toString()); // Processing what the user has typed into the search bar
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Applies a filter to the routes in the database based on the currently selected filter button.
     * @param field The field to filter by (rating, difficulty, location, or slope)
     */
    private void applyFilter(String field) {
        // Difficulty filter that sorts routes from easy to moderate to hard to expert difficulties
        if (field.equals(Route.FIELD_DIFFICULTY)) {
            mQuery = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes")
                    .orderBy(Route.FIELD_DIFFICULTY_ORDER)
                    .orderBy(Route.FIELD_AVG_RATING, Query.Direction.DESCENDING);
        }
        // Slope filter that sorts routes from gentle to steep to very steep slopes
        else if (field.equals(Route.FIELD_SLOPE)) {
            mQuery = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes")
                    .orderBy(Route.FIELD_SLOPE_ORDER)
                    .orderBy(Route.FIELD_AVG_RATING, Query.Direction.DESCENDING);
        }
        // Rating filter sorts routes by descending order of average rating
        else if (field.equals(Route.FIELD_AVG_RATING)) {
            mQuery = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes").orderBy(field, Query.Direction.DESCENDING);
        }

        // Location filter sorts routes by ascending order of city name strings
        else {
            mQuery = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes").orderBy(field, Query.Direction.ASCENDING);
        }

        // Update the adapter with the new query
        mAdapter.setQuery(mQuery);
    }

    /**
     * Filter routes based on user's input into the search bar
     * @param searchText The user's input into the search bar
     */
    private void applySearch(String searchText) {
        // If user has not selected a filter option (button), then don't filter routes via searching
        if (selectedFilterButton == null) {
            // Default behavior if no filter button is selected, where no sorting is done
            mQuery = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes");
        }
        else {
            // Determine the field based on the currently selected filter button
            String field = null;
            if (selectedFilterButton == filterLocationButton) {
                field = Route.FIELD_CITY;
            } else if (selectedFilterButton == filterDifficultyButton) {
                field = Route.FIELD_DIFFICULTY;
            } else if (selectedFilterButton == filterSlopeButton) {
                field = Route.FIELD_SLOPE;
            } else if (selectedFilterButton == filterRatingButton) {
                field = Route.FIELD_AVG_RATING;
            }

            // if the currently selected filter button is anything other than Ratings, then filter by the search text and fix input if needed
            if (field != null  && !field.equals(Route.FIELD_AVG_RATING) && !searchText.isEmpty()) {
                // Use case-insensitive searching with Firestore's array-contains or equality logic
                mQuery = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes")
                        .whereEqualTo(field, capitalizeFirstLetter(searchText)) // Adjust string since Firebase is case-sensitive
                        .orderBy(Route.FIELD_AVG_RATING, Query.Direction.DESCENDING); // sort by descending avg rating by default
            }
            // if the currently selected filter button is Ratings, then convert user input to an integer to show all routes with similar ratings
            else if (field != null && field.equals(Route.FIELD_AVG_RATING) && !searchText.isEmpty()) {
                // Convert search text to an integer
                int ratingValue = Integer.parseInt(searchText);

                mQuery = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes")
                        .whereGreaterThanOrEqualTo(field, ratingValue) // ratingValue is a lower bound
                        .whereLessThan(field, ratingValue+1)// ratingValue+1 is an upper bound
                        .orderBy(Route.FIELD_AVG_RATING, Query.Direction.DESCENDING); // sort by descending avg rating by default
            }
            else {
                applyFilter(field);
            }
        }

        // Update the adapter with the new query
        mAdapter.setQuery(mQuery);
    }

    /**
     * Capitalize the first letter of each word in user's search input since Firebase is case-sensitive.
     * @param text The text that the user has entered into the search bar.
     * @return Modified text where the first letter in each word is capitalized.
     */
    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] words = text.split(" ");
        StringBuilder capitalizedText = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                capitalizedText.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return capitalizedText.toString().trim();
    }

    /**
     * Starts listening to Firestore updates.
     */
    @Override
    protected void onStart() {
        super.onStart();

        // Start listening to Firestore updates
        if (mAdapter != null) {
            mAdapter.startListening();
        }
    }

    /**
     * Stops listening to Firestore updates.
     */
    @Override
    protected void onStop() {
        super.onStop();

        // Stop listening to Firestore updates
        if (mAdapter != null) {
            mAdapter.stopListening();
        }
    }

    /**
     * Generates 2 random routes and adds them to Firestore when "Test Routes" button is pressed.
     */
    private void generateRoutes() {
        CollectionReference routes = mFirestore.collection(isCommunityView() ? "community_routes" : "user_routes");

        // Generate and add 2 random Route objects to Firestore
        for (int i = 0; i < 2; i++) {
            Route randomRoute = RouteUtil.getRandom(this);
            routes.add(randomRoute);
        }
    }

    /**
     * When a route is clicked, user will be redirected to a view that contains more information about the route
     * @param route The route that was clicked
     */
    @Override
    public void onRouteSelected(DocumentSnapshot route) {
        Intent intent = new Intent(this, RouteDetailActivity.class);
        intent.putExtra(RouteDetailActivity.KEY_ROUTE_ID, route.getId());
        intent.putExtra(RouteDetailActivity.KEY_ROUTE_COLLECTION,
                isCommunityView() ? "community_routes" : "user_routes");

        startActivity(intent);
    }
}
