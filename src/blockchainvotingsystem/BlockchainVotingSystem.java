package blockchainvotingsystem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BlockchainVotingSystem {

    static class Candidate {
        int id;
        String name;
        int voteCount;
        java.util.Date createdDate;

        Candidate(int id, String name, int voteCount, java.util.Date createdDate) {
            this.id = id;
            this.name = name;
            this.voteCount = voteCount;
            this.createdDate = createdDate;
        }
    }

    static class Voter {
        String address;
        boolean authorized;
        boolean voted;
        int vote;
        java.util.Date registrationDate;

        Voter(String address, boolean authorized, boolean voted, int vote, java.util.Date registrationDate) {
            this.address = address;
            this.authorized = authorized;
            this.voted = voted;
            this.vote = vote;
            this.registrationDate = registrationDate;
        }
    }

    private final String electionName;
    private final String owner;
    private final Connection connection;

    // Constructor and setup
    public BlockchainVotingSystem(String owner, String electionName) throws SQLException {
        this.owner = owner;
        this.electionName = electionName;
        this.connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/voting_system", "reynarld", "methurey");
    }

    // Add Candidate to the system
    public void addCandidate(String name) throws SQLException {
        String query = "INSERT INTO candidates (name, vote_count, created_date) VALUES (?, 0, NOW())";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        }
    }

    // Register a Voter (Only once per voter)
    public void registerVoter(String voterAddress) throws SQLException {
        String query = "INSERT INTO voters (address, authorized, voted, vote, registration_date) VALUES (?, FALSE, FALSE, -1, NOW()) " +
                       "ON DUPLICATE KEY UPDATE authorized = FALSE, voted = FALSE, vote = -1";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, voterAddress);
            stmt.executeUpdate();
        }
    }

    // Authorize the Voter (Once they've registered and can vote)
    public void authorizeVoter(String voterAddress) throws SQLException {
        String query = "UPDATE voters SET authorized = TRUE WHERE address = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, voterAddress);
            stmt.executeUpdate();
        }
    }

    // Cast a vote for a candidate
    public void vote(String voterAddress, int candidateId) throws Exception {
        // Check if the voter is authorized and hasn't voted yet
        String voterQuery = "SELECT authorized, voted FROM voters WHERE address = ?";
        try (PreparedStatement voterStmt = connection.prepareStatement(voterQuery)) {
            voterStmt.setString(1, voterAddress);
            try (ResultSet rs = voterStmt.executeQuery()) {
                if (!rs.next()) {
                    throw new Exception("Voter not found.");
                }
                boolean isAuthorized = rs.getBoolean("authorized");
                boolean hasVoted = rs.getBoolean("voted");

                if (!isAuthorized) {
                    throw new Exception("You are not authorized to vote. Please get authorized first.");
                }
                if (hasVoted) {
                    throw new Exception("You have already voted.");
                }
            }
        }

        connection.setAutoCommit(false);
        try {
            // Update the candidate's vote count
            String updateVoteQuery = "UPDATE candidates SET vote_count = vote_count + 1 WHERE id = ?";
            try (PreparedStatement updateStmt = connection.prepareStatement(updateVoteQuery)) {
                updateStmt.setInt(1, candidateId);
                updateStmt.executeUpdate();
            }

            // Mark the voter as having voted
            String updateVoterQuery = "UPDATE voters SET voted = TRUE, vote = ? WHERE address = ?";
            try (PreparedStatement voterUpdateStmt = connection.prepareStatement(updateVoterQuery)) {
                voterUpdateStmt.setInt(1, candidateId);
                voterUpdateStmt.setString(2, voterAddress);
                voterUpdateStmt.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // Get all candidates and their vote counts
    public List<Candidate> getAllCandidates() throws SQLException {
        String query = "SELECT id, name, vote_count, created_date FROM candidates";
        List<Candidate> candidates = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                candidates.add(new Candidate(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getInt("vote_count"),
                    new java.util.Date(rs.getDate("created_date").getTime()) // Convert sql.Date to util.Date
                ));
            }
        }
        return candidates;
    }

    // Display results
    public String getVotingResults() throws SQLException {
        StringBuilder results = new StringBuilder();
        List<Candidate> candidates = getAllCandidates();
        for (Candidate candidate : candidates) {
            results.append(candidate.name).append(": ").append(candidate.voteCount).append(" votes\n");
        }
        return results.toString();
    }

    // Main function to run the election with UI
    public static void main(String[] args) {
        try {
            BlockchainVotingSystem votingSystem = new BlockchainVotingSystem("ownerAddress", "Presidential Election");

            // Add candidates
            votingSystem.addCandidate("Alice");
            votingSystem.addCandidate("Bob");

            // Create JFrame for user interface
            JFrame frame = new JFrame("Blockchain Voting System");
            frame.setSize(400, 350);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new FlowLayout());

            // Registration components
            JLabel registrationLabel = new JLabel("Enter your address to register:");
            JTextField addressField = new JTextField(20);
            JButton registerButton = new JButton("Register");

            // Authorization components
            JButton authorizeButton = new JButton("Authorize");

            // Voting components
            JLabel voteLabel = new JLabel("Select candidate to vote:");
            JComboBox<String> candidateComboBox = new JComboBox<>();
            candidateComboBox.addItem("Select a candidate");
            candidateComboBox.addItem("Alice");
            candidateComboBox.addItem("Bob");
            JButton voteButton = new JButton("Cast Vote");

            // Results components
            JTextArea resultsArea = new JTextArea(5, 30);
            resultsArea.setEditable(false);
            JScrollPane resultsScrollPane = new JScrollPane(resultsArea);

            // Add components to frame
            frame.add(registrationLabel);
            frame.add(addressField);
            frame.add(registerButton);
            frame.add(authorizeButton);
            frame.add(voteLabel);
            frame.add(candidateComboBox);
            frame.add(voteButton);
            frame.add(resultsScrollPane);

            // Register button action
            registerButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String address = addressField.getText().trim();
                    if (!address.isEmpty()) {
                        try {
                            votingSystem.registerVoter(address);
                            JOptionPane.showMessageDialog(frame, "Registration Successful. You can now authorize and vote!");
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(frame, "Error registering voter: " + ex.getMessage());
                        }
                    } else {
                        JOptionPane.showMessageDialog(frame, "Please enter a valid address.");
                    }
                }
            });

            // Authorize button action
            authorizeButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String address = addressField.getText().trim();
                    if (address.isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "Please register first.");
                        return;
                    }
                    try {
                        votingSystem.authorizeVoter(address);
                        JOptionPane.showMessageDialog(frame, "You have been authorized to vote!");
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(frame, "Error authorizing voter: " + ex.getMessage());
                    }
                }
            });

            // Vote button action
            voteButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String address = addressField.getText().trim();
                    if (address.isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "Please register first.");
                        return;
                    }

                    int selectedCandidateIndex = candidateComboBox.getSelectedIndex(); // 1-based index
                    if (selectedCandidateIndex == 0) {
                        JOptionPane.showMessageDialog(frame, "Please select a candidate.");
                        return;
                    }

                    try {
                        votingSystem.vote(address, selectedCandidateIndex); // Use the selected index to vote
                        JOptionPane.showMessageDialog(frame, "Vote casted successfully for " +
                                candidateComboBox.getSelectedItem());

                        // After voting, show the updated results
                        resultsArea.setText(votingSystem.getVotingResults());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, "Error casting vote: " + ex.getMessage());
                    }
                }
            });

            // Set the frame visible
            frame.setVisible(true);

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error: " + e.getMessage());
        }
    }
}
